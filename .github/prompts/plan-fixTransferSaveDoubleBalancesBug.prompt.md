## Plan: Fix “Save Transfer Doubles Balances” Bug

The bug happens because `updateTransfer(...)` computes the “apply new transfer” balances from a stale `allAccounts.value` snapshot taken before the “revert old transfer” writes, so an unchanged Save effectively applies the transfer twice. The fix is to avoid read-modify-write on stale snapshots: skip balance updates when nothing balance-affecting changed, and for real changes compute deltas from one consistent baseline (ideally in a Room `@Transaction`).

### Steps
1. Add an “unchanged transfer” guard in `ExpenseViewModel.updateTransfer(transfer: TransferHistory)` comparing source/dest/amount/currency (and optionally date/comment).
2. If unchanged (or only date/comment changed), update only `transfer_history` via `transferHistoryRepository.update(...)` and return.
3. For balance-affecting changes, compute net deltas for affected accounts in-memory from one baseline: old-source `+oldAmount`, old-dest `-oldAmount`, new-source `-newAmount`, new-dest `+newAmount`.
4. Apply account balance updates once per affected account (handle `source==dest` and overlapping accounts by merging deltas).
5. Preferably wrap steps 3–4 plus `transferHistoryRepository.update(...)` into a single Room `@Transaction` method (new DAO/repository function) to prevent partial updates.
6. Keep scale/rounding consistent (`setScale(2, HALF_UP)`) after applying deltas.

### Further Considerations
1. Use DB-fresh data (transactional read) instead of `allAccounts.value` to avoid async Flow staleness.
