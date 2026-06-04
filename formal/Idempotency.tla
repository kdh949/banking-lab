-------------------------- MODULE Idempotency --------------------------
EXTENDS Naturals, FiniteSets, Sequences

\* IdempotencyKey commands may be retried, but one key must keep exactly
\* one business result and must not create duplicate ledger side effects.

CONSTANTS KeySet, ResultSet, MaxCommands

VARIABLES commands, resultsByKey, postingSideEffectsByKey

vars == <<commands, resultsByKey, postingSideEffectsByKey>>

KnownKeys == {k \in KeySet : resultsByKey[k] # "NONE"}

SeqToSet(seq) == {seq[i] : i \in 1..Len(seq)}

Init ==
  /\ commands = <<>>
  /\ resultsByKey = [k \in KeySet |-> "NONE"]
  /\ postingSideEffectsByKey = [k \in KeySet |-> 0]

CompleteNewCommand ==
  \E key \in KeySet, result \in ResultSet:
    /\ key \notin KnownKeys
    /\ commands' = Append(commands, [key |-> key, result |-> result, sideEffect |-> "POSTING"])
    /\ resultsByKey' = [resultsByKey EXCEPT ![key] = result]
    /\ postingSideEffectsByKey' = [postingSideEffectsByKey EXCEPT ![key] = @ + 1]

RetryKnownCommand ==
  \E key \in KnownKeys:
    /\ commands' = Append(commands, [key |-> key, result |-> resultsByKey[key], sideEffect |-> "NONE"])
    /\ UNCHANGED <<resultsByKey, postingSideEffectsByKey>>

HoldOrFailCommand ==
  \E key \in KeySet:
    /\ key \notin KnownKeys
    /\ commands' = Append(commands, [key |-> key, result |-> "HELD_OR_FAILED", sideEffect |-> "NONE"])
    /\ resultsByKey' = [resultsByKey EXCEPT ![key] = "HELD_OR_FAILED"]
    /\ UNCHANGED postingSideEffectsByKey

Next == CompleteNewCommand \/ RetryKnownCommand \/ HoldOrFailCommand

Spec == Init /\ [][Next]_vars

StateConstraint == Len(commands) <= MaxCommands

SingleBusinessResultPerKey ==
  \A i \in 1..Len(commands) :
    \A j \in 1..Len(commands) :
      commands[i].key = commands[j].key => commands[i].result = commands[j].result

RetryReturnsSameBusinessResult ==
  \A command \in SeqToSet(commands) :
    command.key \in KnownKeys => command.result = resultsByKey[command.key]

NoDuplicateSideEffectForRetry ==
  \A key \in KeySet : postingSideEffectsByKey[key] <= 1

FailedOrHeldCommandNoPosting ==
  \A command \in SeqToSet(commands) :
    command.result = "HELD_OR_FAILED" => command.sideEffect = "NONE"

=============================================================================
