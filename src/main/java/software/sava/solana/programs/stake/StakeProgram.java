package software.sava.solana.programs.stake;

import software.sava.core.accounts.AccountWithSeed;
import software.sava.core.accounts.PublicKey;
import software.sava.core.accounts.SolanaAccounts;
import software.sava.core.accounts.meta.AccountMeta;
import software.sava.core.borsh.Borsh;
import software.sava.core.encoding.ByteUtil;
import software.sava.core.programs.Discriminator;
import software.sava.core.tx.Instruction;

import java.time.Instant;
import java.util.List;
import java.util.OptionalLong;

import static software.sava.core.accounts.PublicKey.PUBLIC_KEY_LENGTH;
import static software.sava.core.accounts.meta.AccountMeta.*;
import static software.sava.core.programs.Discriminator.NATIVE_DISCRIMINATOR_LENGTH;
import static software.sava.core.programs.Discriminator.serializeDiscriminator;

public final class StakeProgram {

  public enum Instructions implements Discriminator {
    // Initialize a stake with lockup and authorization information
    //
    // # Account references
    //   0. '[WRITE]' Uninitialized stake account
    //   1. '[]' Rent sysvar
    //
    // Authorized carries pub keys that must sign staker transactions
    //   and withdrawer transactions.
    // Lockup carries information about withdrawal restrictions
    Initialize(
//        Authorized, Lockup
    ),

    // Authorize a key to manage stake or withdrawal
    //
    // # Account references
    //   0. '[WRITE]' Stake account to be updated
    //   1. '[]' Clock sysvar
    //   2. '[SIGNER]' The stake or withdraw authority
    //   3. Optional: '[SIGNER]' Lockup authority, if updating StakeAuthorize::Withdrawer before
    //      lockup expiration
    Authorize(
//        Pubkey, StakeAuthorize
    ),

    // Delegate a stake to a particular vote account
    //
    // # Account references
    //   0. '[WRITE]' Initialized stake account to be delegated
    //   1. '[]' Vote account to which this stake will be delegated
    //   2. '[]' Clock sysvar
    //   3. '[]' Stake history sysvar that carries stake warmup/cooldown history
    //   4. '[]' Address of config account that carries stake config
    //   5. '[SIGNER]' Stake authority
    //
    // The entire balance of the staking account is staked.  DelegateStake
    //   can be called multiple times, but re-delegation is delayed
    //   by one epoch
    DelegateStake,

    // Split u64 tokens and stake off a stake account into another stake account.
    //
    // # Account references
    //   0. '[WRITE]' Stake account to be split; must be in the Initialized or Stake state
    //   1. '[WRITE]' Uninitialized stake account that will take the split-off amount
    //   2. '[SIGNER]' Stake authority
    Split(
//        u64
    ),

    // Withdraw unstaked lamports from the stake account
    //
    // # Account references
    //   0. '[WRITE]' Stake account from which to withdraw
    //   1. '[WRITE]' Recipient account
    //   2. '[]' Clock sysvar
    //   3. '[]' Stake history sysvar that carries stake warmup/cooldown history
    //   4. '[SIGNER]' Withdraw authority
    //   5. Optional: '[SIGNER]' Lockup authority, if before lockup expiration
    //
    // The u64 is the portion of the stake account balance to be withdrawn,
    //    must be '<= StakeAccount.lamports - staked_lamports'.
    Withdraw(
//        u64
    ),

    // Deactivates the stake in the account
    //
    // # Account references
    //   0. '[WRITE]' Delegated stake account
    //   1. '[]' Clock sysvar
    //   2. '[SIGNER]' Stake authority
    Deactivate,

    // Set stake lockup
    //
    // If a lockup is not active, the withdrawal authority may set a new lockup
    // If a lockup is active, the lockup custodian may update the lockup parameters
    //
    // # Account references
    //   0. '[WRITE]' Initialized stake account
    //   1. '[SIGNER]' Lockup authority or withdraw authority
    SetLockup(
//        LockupArgs
    ),

    // Merge two stake accounts.
    //
    // Both accounts must have identical lockup and authority keys. A merge
    // is possible between two stakes in the following states with no additional
    // conditions:
    //
    // * two deactivated stakes
    // * an inactive stake into an activating stake during its activation epoch
    //
    // For the following cases, the voter pubkey and vote credits observed must match:
    //
    // * two activated stakes
    // * two activating accounts that share an activation epoch, during the activation epoch
    //
    // All other combinations of stake states will fail to merge, including all
    // "transient" states, where a stake is activating or deactivating with a
    // non-zero effective stake.
    //
    // # Account references
    //   0. '[WRITE]' Destination stake account for the merge
    //   1. '[WRITE]' Source stake account for to merge.  This account will be drained
    //   2. '[]' Clock sysvar
    //   3. '[]' Stake history sysvar that carries stake warmup/cooldown history
    //   4. '[SIGNER]' Stake authority
    Merge,

    // Authorize a key to manage stake or withdrawal with a derived key
    //
    // # Account references
    //   0. '[WRITE]' Stake account to be updated
    //   1. '[SIGNER]' Base key of stake or withdraw authority
    //   2. '[]' Clock sysvar
    //   3. Optional: '[SIGNER]' Lockup authority, if updating StakeAuthorize::Withdrawer before
    //      lockup expiration
    AuthorizeWithSeed(
//        AuthorizeWithSeedArgs
    ),

    // Initialize a stake with authorization information
    //
    // This instruction is similar to 'Initialize' except that the withdrawal authority
    // must be a signer, and no lockup is applied to the account.
    //
    // # Account references
    //   0. '[WRITE]' Uninitialized stake account
    //   1. '[]' Rent sysvar
    //   2. '[]' The stake authority
    //   3. '[SIGNER]' The withdrawal authority
    //
    InitializeChecked,

    // Authorize a key to manage stake or withdrawal
    //
    // This instruction behaves like 'Authorize' with the additional requirement that the new
    // stake or withdraw authority must also be a signer.
    //
    // # Account references
    //   0. '[WRITE]' Stake account to be updated
    //   1. '[]' Clock sysvar
    //   2. '[SIGNER]' The stake or withdraw authority
    //   3. '[SIGNER]' The new stake or withdraw authority
    //   4. Optional: '[SIGNER]' Lockup authority, if updating StakeAuthorize::Withdrawer before
    //      lockup expiration
    AuthorizeChecked(
//        StakeAuthorize
    ),

    // Authorize a key to manage stake or withdrawal with a derived key
    //
    // This instruction behaves like 'AuthorizeWithSeed' with the additional requirement that
    // the new stake or withdraw authority must also be a signer.
    //
    // # Account references
    //   0. '[WRITE]' Stake account to be updated
    //   1. '[SIGNER]' Base key of stake or withdraw authority
    //   2. '[]' Clock sysvar
    //   3. '[SIGNER]' The new stake or withdraw authority
    //   4. Optional: '[SIGNER]' Lockup authority, if updating StakeAuthorize::Withdrawer before
    //      lockup expiration
    AuthorizeCheckedWithSeed(
//        AuthorizeCheckedWithSeedArgs
    ),

    // Set stake lockup
    //
    // This instruction behaves like 'SetLockup' with the additional requirement that
    // the new lockup authority also be a signer.
    //
    // If a lockup is not active, the withdrawal authority may set a new lockup
    // If a lockup is active, the lockup custodian may update the lockup parameters
    //
    // # Account references
    //   0. '[WRITE]' Initialized stake account
    //   1. '[SIGNER]' Lockup authority or withdraw authority
    //   2. Optional: '[SIGNER]' New lockup authority
    SetLockupChecked(
//        LockupCheckedArgs
    ),

    // Get the minimum stake delegation, in lamports
    //
    // # Account references
    //   None
    //
    // Returns the minimum delegation as a little-endian encoded u64 value.
    // Programs can use the ['get_minimum_delegation()'] helper function to invoke and
    // retrieve the return value for this instruction.
    //
    // ['get_minimum_delegation()']: super::tools::get_minimum_delegation
    GetMinimumDelegation,

    // Deactivate stake delegated to a vote account that has been delinquent for at least
    // 'MINIMUM_DELINQUENT_EPOCHS_FOR_DEACTIVATION' epochs.
    //
    // No signer is required for this instruction as it is a common good to deactivate abandoned
    // stake.
    //
    // # Account references
    //   0. '[WRITE]' Delegated stake account
    //   1. '[]' Delinquent vote account for the delegated stake account
    //   2. '[]' Reference vote account that has voted at least once in the last
    //      'MINIMUM_DELINQUENT_EPOCHS_FOR_DEACTIVATION' epochs
    DeactivateDelinquent,

    // Relegate activated stake to another vote account.
    //
    // Upon success:
    //   * the balance of the delegated stake account will be reduced to the undelegated amount in
    //     the account (rent exempt minimum and any additional lamports not part of the delegation),
    //     and scheduled for deactivation.
    //   * the provided uninitialized stake account will receive the original balance of the
    //     delegated stake account, minus the rent exempt minimum, and scheduled for activation to
    //     the provided vote account. Any existing lamports in the uninitialized stake account
    //     will also be included in the re-delegation.
    //
    // # Account references
    //   0. '[WRITE]' Delegated stake account to be re-delegated. The account must be fully
    //      activated and carry a balance greater than or equal to the minimum delegation amount
    //      plus rent exempt minimum
    //   1. '[WRITE]' Uninitialized stake account that will hold the re-delegated stake
    //   2. '[]' Vote account to which this stake will be re-delegated
    //   3. '[]' Address of config account that carries stake config
    //   4. '[SIGNER]' Stake authority
    //
    Redelegate;

    private final byte[] data;

    Instructions() {
      this.data = serializeDiscriminator(this);
    }

    public byte[] data() {
      return this.data;
    }
  }

  public static Instruction withdraw(final SolanaAccounts solanaAccounts,
                                     final List<AccountMeta> keys,
                                     final long lamports) {
    final byte[] data = new byte[NATIVE_DISCRIMINATOR_LENGTH + Long.BYTES];
    Instructions.Withdraw.write(data);
    ByteUtil.putInt64LE(data, NATIVE_DISCRIMINATOR_LENGTH, lamports);

    return Instruction.createInstruction(solanaAccounts.invokedStakeProgram(), keys, data);
  }

  public static Instruction withdraw(final SolanaAccounts solanaAccounts,
                                     final PublicKey stakeAccount,
                                     final PublicKey recipient,
                                     final PublicKey withdrawAuthority,
                                     final long lamports) {
    final var keys = List.of(
        createWrite(stakeAccount),
        createWrite(recipient),
        solanaAccounts.readClockSysVar(),
        solanaAccounts.readStakeHistorySysVar(),
        createReadOnlySigner(withdrawAuthority)
    );
    return withdraw(solanaAccounts, keys, lamports);
  }

  public static Instruction withdraw(final SolanaAccounts solanaAccounts,
                                     final PublicKey stakeAccount,
                                     final PublicKey recipient,
                                     final PublicKey withdrawAuthority,
                                     final PublicKey lockupAuthority,
                                     final long lamports) {
    if (lockupAuthority == null) {
      return withdraw(solanaAccounts, stakeAccount, recipient, withdrawAuthority, lamports);
    }
    final var keys = List.of(
        createWrite(stakeAccount),
        createWrite(recipient),
        solanaAccounts.readClockSysVar(),
        solanaAccounts.readStakeHistorySysVar(),
        createReadOnlySigner(withdrawAuthority),
        createReadOnlySigner(lockupAuthority)
    );
    return withdraw(solanaAccounts, keys, lamports);
  }

  public static Instruction split(final SolanaAccounts solanaAccounts,
                                  final PublicKey splitStakeAccount,
                                  final PublicKey unInitializedStakeAccount,
                                  final PublicKey stakeAuthority,
                                  final long lamports) {
    final var keys = List.of(
        createWrite(splitStakeAccount),
        createWrite(unInitializedStakeAccount),
        createReadOnlySigner(stakeAuthority)
    );


    final byte[] data = new byte[NATIVE_DISCRIMINATOR_LENGTH + Long.BYTES];
    Instructions.Split.write(data);
    ByteUtil.putInt64LE(data, NATIVE_DISCRIMINATOR_LENGTH, lamports);

    return Instruction.createInstruction(solanaAccounts.invokedStakeProgram(), keys, data);
  }

  public static Instruction merge(final SolanaAccounts solanaAccounts,
                                  final PublicKey destinationStakeAccount,
                                  final PublicKey srcStakeAccount,
                                  final PublicKey stakeAuthority) {
    final var keys = List.of(
        createWrite(destinationStakeAccount),
        createWrite(srcStakeAccount),
        solanaAccounts.readClockSysVar(),
        solanaAccounts.readStakeHistorySysVar(),
        createReadOnlySigner(stakeAuthority)
    );

    return Instruction.createInstruction(solanaAccounts.invokedStakeProgram(), keys, Instructions.Merge.data);
  }

  public static Instruction initialize(final SolanaAccounts solanaAccounts,
                                       final PublicKey unInitializedStakeAccount,
                                       final PublicKey staker,
                                       final PublicKey withdrawer) {
    return initialize(solanaAccounts, unInitializedStakeAccount, staker, withdrawer, LockUp.NO_LOCKUP);
  }

  public static Instruction initialize(final SolanaAccounts solanaAccounts,
                                       final PublicKey unInitializedStakeAccount,
                                       final PublicKey staker,
                                       final PublicKey withdrawer,
                                       final LockUp lockUp) {
    final var keys = List.of(
        createWrite(unInitializedStakeAccount),
        solanaAccounts.readRentSysVar()
    );

    final byte[] data = new byte[NATIVE_DISCRIMINATOR_LENGTH + PUBLIC_KEY_LENGTH + PUBLIC_KEY_LENGTH + LockUp.BYTES];
    int i = Instructions.Initialize.write(data);
    staker.write(data, i);
    i += PUBLIC_KEY_LENGTH;
    i += withdrawer.write(data, i);
    lockUp.write(data, i);

    return Instruction.createInstruction(solanaAccounts.invokedStakeProgram(), keys, data);
  }

  public static Instruction initializeChecked(final SolanaAccounts solanaAccounts,
                                              final PublicKey unInitializedStakeAccount,
                                              final PublicKey staker,
                                              final PublicKey withdrawer) {
    final var keys = List.of(
        createWrite(unInitializedStakeAccount),
        solanaAccounts.readRentSysVar(),
        createRead(staker),
        createReadOnlySigner(withdrawer)
    );

    return Instruction.createInstruction(solanaAccounts.invokedStakeProgram(), keys, Instructions.InitializeChecked.data);
  }

  public static Instruction authorize(final SolanaAccounts solanaAccounts,
                                      final List<AccountMeta> keys,
                                      final PublicKey newAuthority,
                                      final StakeAuthorize stakeAuthorize) {
    final byte[] data = new byte[NATIVE_DISCRIMINATOR_LENGTH + PUBLIC_KEY_LENGTH + stakeAuthorize.l()];
    Instructions.Authorize.write(data);
    newAuthority.write(data, NATIVE_DISCRIMINATOR_LENGTH);
    stakeAuthorize.write(data, NATIVE_DISCRIMINATOR_LENGTH + PUBLIC_KEY_LENGTH);
    return Instruction.createInstruction(solanaAccounts.invokedStakeProgram(), keys, data);
  }

  public static Instruction authorize(final SolanaAccounts solanaAccounts,
                                      final PublicKey stakeAccount,
                                      final PublicKey stakeOrWithdrawAuthority,
                                      final PublicKey newAuthority,
                                      final StakeAuthorize stakeAuthorize) {
    final var keys = List.of(
        createWrite(stakeAccount),
        solanaAccounts.readClockSysVar(),
        createReadOnlySigner(stakeOrWithdrawAuthority)
    );
    return authorize(solanaAccounts, keys, newAuthority, stakeAuthorize);
  }

  public static Instruction authorize(final SolanaAccounts solanaAccounts,
                                      final PublicKey stakeAccount,
                                      final PublicKey stakeOrWithdrawAuthority,
                                      final PublicKey lockupAuthority,
                                      final PublicKey newAuthority,
                                      final StakeAuthorize stakeAuthorize) {
    if (lockupAuthority == null) {
      return authorize(
          solanaAccounts, stakeAccount, stakeOrWithdrawAuthority,
          newAuthority, stakeAuthorize
      );
    }
    final var keys = List.of(
        createWrite(stakeAccount),
        solanaAccounts.readClockSysVar(),
        createReadOnlySigner(stakeOrWithdrawAuthority),
        createReadOnlySigner(lockupAuthority)
    );
    return authorize(solanaAccounts, keys, newAuthority, stakeAuthorize);
  }

  public static Instruction authorizeChecked(final SolanaAccounts solanaAccounts,
                                             final List<AccountMeta> keys,
                                             final StakeAuthorize stakeAuthorize) {
    final byte[] data = new byte[NATIVE_DISCRIMINATOR_LENGTH + stakeAuthorize.l()];
    Instructions.AuthorizeChecked.write(data);
    stakeAuthorize.write(data, NATIVE_DISCRIMINATOR_LENGTH);
    return Instruction.createInstruction(solanaAccounts.invokedStakeProgram(), keys, data);
  }

  public static Instruction authorizeChecked(final SolanaAccounts solanaAccounts,
                                             final PublicKey stakeAccount,
                                             final PublicKey stakeOrWithdrawAuthority,
                                             final PublicKey newStakeOrWithdrawAuthority,
                                             final StakeAuthorize stakeAuthorize) {
    final var keys = List.of(
        createWrite(stakeAccount),
        solanaAccounts.readClockSysVar(),
        createReadOnlySigner(stakeOrWithdrawAuthority),
        createReadOnlySigner(newStakeOrWithdrawAuthority)
    );
    return authorizeChecked(solanaAccounts, keys, stakeAuthorize);
  }

  public static Instruction authorizeChecked(final SolanaAccounts solanaAccounts,
                                             final PublicKey stakeAccount,
                                             final PublicKey stakeOrWithdrawAuthority,
                                             final PublicKey newStakeOrWithdrawAuthority,
                                             final PublicKey lockupAuthority,
                                             final StakeAuthorize stakeAuthorize) {
    if (lockupAuthority == null) {
      return authorizeChecked(
          solanaAccounts, stakeAccount, stakeOrWithdrawAuthority, newStakeOrWithdrawAuthority,
          stakeAuthorize
      );
    }
    final var keys = List.of(
        createWrite(stakeAccount),
        solanaAccounts.readClockSysVar(),
        createReadOnlySigner(stakeOrWithdrawAuthority),
        createReadOnlySigner(newStakeOrWithdrawAuthority),
        createReadOnlySigner(lockupAuthority)
    );
    return authorizeChecked(solanaAccounts, keys, stakeAuthorize);
  }

  private static Instruction authorizeWithSeed(final SolanaAccounts solanaAccounts,
                                               final List<AccountMeta> keys,
                                               final PublicKey newAuthorizedPublicKey,
                                               final StakeAuthorize stakeAuthorize,
                                               final AccountWithSeed authoritySeed,
                                               final PublicKey authorityOwner) {
    final byte[] authoritySeedBytes = authoritySeed.asciiSeed();
    final byte[] data = new byte[NATIVE_DISCRIMINATOR_LENGTH
        + PUBLIC_KEY_LENGTH
        + stakeAuthorize.l()
        + Borsh.lenVector(authoritySeedBytes)
        + PUBLIC_KEY_LENGTH];
    int i = Instructions.AuthorizeWithSeed.write(data);
    i += newAuthorizedPublicKey.write(data, i);
    i += stakeAuthorize.write(data, i);
    i += Borsh.writeVector(authoritySeedBytes, data, i);
    authorityOwner.write(data, i);
    return Instruction.createInstruction(solanaAccounts.invokedStakeProgram(), keys, data);
  }

  public static Instruction authorizeWithSeed(final SolanaAccounts solanaAccounts,
                                              final PublicKey stakeAccount,
                                              final AccountWithSeed baseKeyOrWithdrawAuthority,
                                              final PublicKey newAuthorizedPublicKey,
                                              final StakeAuthorize stakeAuthorize,
                                              final PublicKey authorityOwner) {
    final var keys = List.of(
        createWrite(stakeAccount),
        createReadOnlySigner(baseKeyOrWithdrawAuthority.publicKey()),
        solanaAccounts.readClockSysVar()
    );
    return authorizeWithSeed(solanaAccounts, keys, newAuthorizedPublicKey, stakeAuthorize, baseKeyOrWithdrawAuthority, authorityOwner);
  }

  public static Instruction authorizeWithSeed(final SolanaAccounts solanaAccounts,
                                              final PublicKey stakeAccount,
                                              final AccountWithSeed baseKeyOrWithdrawAuthority,
                                              final PublicKey lockupAuthority,
                                              final PublicKey newAuthorizedPublicKey,
                                              final StakeAuthorize stakeAuthorize,
                                              final PublicKey authorityOwner) {
    if (lockupAuthority == null) {
      return authorizeWithSeed(
          solanaAccounts, stakeAccount, baseKeyOrWithdrawAuthority,
          newAuthorizedPublicKey, stakeAuthorize, authorityOwner
      );
    }
    final var keys = List.of(
        createWrite(stakeAccount),
        createReadOnlySigner(baseKeyOrWithdrawAuthority.publicKey()),
        solanaAccounts.readClockSysVar(),
        createReadOnlySigner(lockupAuthority)
    );
    return authorizeWithSeed(solanaAccounts, keys, newAuthorizedPublicKey, stakeAuthorize, baseKeyOrWithdrawAuthority, authorityOwner);
  }

  public static Instruction authorizeCheckedWithSeed(final SolanaAccounts solanaAccounts,
                                                     final List<AccountMeta> keys,
                                                     final StakeAuthorize stakeAuthorize,
                                                     final AccountWithSeed authoritySeed,
                                                     final PublicKey authorityOwner) {
    final byte[] authoritySeedBytes = authoritySeed.asciiSeed();
    final byte[] data = new byte[NATIVE_DISCRIMINATOR_LENGTH
        + stakeAuthorize.l()
        + Borsh.lenVector(authoritySeedBytes)
        + PUBLIC_KEY_LENGTH];

    int i = Instructions.AuthorizeCheckedWithSeed.write(data);
    i += stakeAuthorize.write(data, i);
    i += Borsh.writeVector(authoritySeedBytes, data, i);
    authorityOwner.write(data, i);
    return Instruction.createInstruction(solanaAccounts.invokedStakeProgram(), keys, data);
  }

  public static Instruction authorizeCheckedWithSeed(final SolanaAccounts solanaAccounts,
                                                     final PublicKey stakeAccount,
                                                     final AccountWithSeed baseKeyOrWithdrawAuthority,
                                                     final PublicKey stakeOrWithdrawAuthority,
                                                     final StakeAuthorize stakeAuthorize,
                                                     final PublicKey authorityOwner) {
    final var keys = List.of(
        createWrite(stakeAccount),
        createReadOnlySigner(baseKeyOrWithdrawAuthority.publicKey()),
        solanaAccounts.readClockSysVar(),
        createReadOnlySigner(stakeOrWithdrawAuthority)
    );
    return authorizeCheckedWithSeed(solanaAccounts, keys, stakeAuthorize, baseKeyOrWithdrawAuthority, authorityOwner);
  }

  public static Instruction authorizeCheckedWithSeed(final SolanaAccounts solanaAccounts,
                                                     final PublicKey stakeAccount,
                                                     final AccountWithSeed baseKeyOrWithdrawAuthority,
                                                     final PublicKey stakeOrWithdrawAuthority,
                                                     final PublicKey lockupAuthority,
                                                     final StakeAuthorize stakeAuthorize,
                                                     final PublicKey authorityOwner) {
    if (lockupAuthority == null) {
      return authorizeCheckedWithSeed(
          solanaAccounts, stakeAccount, baseKeyOrWithdrawAuthority, stakeOrWithdrawAuthority,
          stakeAuthorize, authorityOwner
      );
    }
    final var keys = List.of(
        createWrite(stakeAccount),
        createReadOnlySigner(baseKeyOrWithdrawAuthority.publicKey()),
        solanaAccounts.readClockSysVar(),
        createReadOnlySigner(stakeOrWithdrawAuthority),
        createReadOnlySigner(lockupAuthority)
    );
    return authorizeCheckedWithSeed(solanaAccounts, keys, stakeAuthorize, baseKeyOrWithdrawAuthority, authorityOwner);
  }

  public static Instruction setLockup(final SolanaAccounts solanaAccounts,
                                      final PublicKey initializedStakeAccount,
                                      final PublicKey lockupOrWithdrawAuthority,
                                      final Instant timestamp,
                                      final OptionalLong epoch,
                                      final PublicKey custodian) {
    final var keys = List.of(
        createWrite(initializedStakeAccount),
        createReadOnlySigner(lockupOrWithdrawAuthority)
    );

    final byte[] data = new byte[NATIVE_DISCRIMINATOR_LENGTH
        + (timestamp == null ? 1 : 1 + Long.BYTES)
        + (epoch.isEmpty() ? 1 : 1 + Long.BYTES)
        + (custodian == null ? 1 : 1 + PUBLIC_KEY_LENGTH)];

    int i = Instructions.SetLockup.write(data);
    i += Borsh.writeOptional(timestamp, data, i);
    i += Borsh.writeOptional(epoch, data, i);
    Borsh.writeOptional(custodian, data, i);

    return Instruction.createInstruction(solanaAccounts.invokedStakeProgram(), keys, data);
  }

  public static Instruction setLockupChecked(final SolanaAccounts solanaAccounts,
                                             final List<AccountMeta> keys,
                                             final Instant timestamp,
                                             final OptionalLong epoch) {
    final byte[] data = new byte[NATIVE_DISCRIMINATOR_LENGTH
        + (timestamp == null ? 1 : 1 + Long.BYTES)
        + (epoch.isEmpty() ? 1 : 1 + Long.BYTES)];

    int i = Instructions.SetLockupChecked.write(data);
    i += Borsh.writeOptional(timestamp, data, i);
    Borsh.writeOptional(epoch, data, i);

    return Instruction.createInstruction(solanaAccounts.invokedStakeProgram(), keys, data);
  }

  public static Instruction setLockupChecked(final SolanaAccounts solanaAccounts,
                                             final PublicKey initializedStakeAccount,
                                             final PublicKey lockupOrWithdrawAuthority,
                                             final Instant timestamp,
                                             final OptionalLong epoch) {
    final var keys = List.of(
        createWrite(initializedStakeAccount),
        createReadOnlySigner(lockupOrWithdrawAuthority)
    );
    return setLockupChecked(solanaAccounts, keys, timestamp, epoch);
  }

  public static Instruction setLockupChecked(final SolanaAccounts solanaAccounts,
                                             final PublicKey initializedStakeAccount,
                                             final PublicKey lockupOrWithdrawAuthority,
                                             final PublicKey newLockupAuthority,
                                             final Instant timestamp,
                                             final OptionalLong epoch) {
    if (newLockupAuthority == null) {
      return setLockupChecked(
          solanaAccounts, initializedStakeAccount, lockupOrWithdrawAuthority,
          timestamp, epoch
      );
    }
    final var keys = List.of(
        createWrite(initializedStakeAccount),
        createReadOnlySigner(lockupOrWithdrawAuthority),
        createReadOnlySigner(newLockupAuthority)
    );
    return setLockupChecked(solanaAccounts, keys, timestamp, epoch);
  }

  public static Instruction deactivateDelinquent(final SolanaAccounts solanaAccounts,
                                                 final PublicKey delegatedStakeAccount,
                                                 final PublicKey delinquentVoteAccount,
                                                 final PublicKey referenceVoteAccount) {
    final var keys = List.of(
        createWrite(delegatedStakeAccount),
        createRead(delinquentVoteAccount),
        createRead(referenceVoteAccount)
    );
    return Instruction.createInstruction(
        solanaAccounts.invokedStakeProgram(),
        keys,
        Instructions.DeactivateDelinquent.data
    );
  }

  public static Instruction deactivate(final SolanaAccounts solanaAccounts,
                                       final PublicKey delegatedStakeAccount,
                                       final PublicKey stakeAuthority) {
    final var keys = List.of(
        createWrite(delegatedStakeAccount),
        solanaAccounts.readClockSysVar(),
        createReadOnlySigner(stakeAuthority)
    );
    return Instruction.createInstruction(
        solanaAccounts.invokedStakeProgram(),
        keys,
        Instructions.Deactivate.data
    );
  }

  public static Instruction delegateStake(final SolanaAccounts solanaAccounts,
                                          final PublicKey initializedStakeAccount,
                                          final PublicKey validatorVoteAccount,
                                          final PublicKey stakeAuthority) {
    final var keys = List.of(
        createWrite(initializedStakeAccount),
        createRead(validatorVoteAccount),
        solanaAccounts.readClockSysVar(),
        solanaAccounts.readStakeHistorySysVar(),
        solanaAccounts.readStakeConfig(),
        createReadOnlySigner(stakeAuthority)
    );
    return Instruction.createInstruction(
        solanaAccounts.invokedStakeProgram(),
        keys,
        Instructions.DelegateStake.data
    );
  }

  public static Instruction reDelegate(final SolanaAccounts solanaAccounts,
                                       final PublicKey delegatedStakeAccount,
                                       final PublicKey uninitializedStakeAccount,
                                       final PublicKey validatorVoteAccount,
                                       final PublicKey stakeAuthority) {
    final var keys = List.of(
        createWrite(delegatedStakeAccount),
        createWrite(uninitializedStakeAccount),
        createRead(validatorVoteAccount),
        solanaAccounts.readStakeConfig(),
        createReadOnlySigner(stakeAuthority)
    );
    return Instruction.createInstruction(
        solanaAccounts.invokedStakeProgram(),
        keys,
        Instructions.Redelegate.data
    );
  }

  private StakeProgram() {
  }
}
