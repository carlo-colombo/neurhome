# Task 4: Make Home Model Training Cancellable

**Status: Done.** `trainBlocking` is now `suspend` with `ensureActive()` per epoch
and every 64 samples. Review follow-up also made the training-data replay
cooperative (`PairwiseHomeAppTrainingDataFactory.buildBlocking`, every 1024 rows),
closing the remaining non-cancellable phase. Reranker unit tests still pass.

Source: Code review — Bugs 4. Priority: P1.

## Problem

`refreshHomeModel()` (`NeurhomeRepository.kt:320-334, 363`) cancels the previous
job, but `PairwiseHomeAppReranker.trainBlocking` is a tight CPU loop with no
`ensureActive()` or suspension point, so cancellation does not interrupt it.
Rapid launches can overlap full trainings with last-writer-wins on `homeModel`.

## Scope

- Make `trainBlocking` cooperative: accept a `CoroutineScope`/job or check
  `ensureActive()` periodically through the iteration loop.
- Ensure a superseded training stops before a new one starts.
- Keep training on an appropriate dispatcher.

## Acceptance criteria

- Cancelling `refreshHomeModel` actually stops in-flight training.
- Rapid successive refreshes do not run overlapping trainings.
- Existing reranker parity tests and `:app:assembleDevDebug` still pass.

## Dependencies

Related to Task 9 (gating training on `LEARNED`); either can land first.
