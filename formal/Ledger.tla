----------------------------- MODULE Ledger -----------------------------
EXTENDS Naturals, Integers, FiniteSets

\* Concepts covered: Accounts, Transactions, Postings, Debit/Credit,
\* Balance projection, IdempotencyKey, Reversal, Closed business date,
\* held/failed commands that must not produce ledger postings, and
\* approved adjustment references.

CONSTANTS AccountSet, TxnSet, KeySet, DateSet, ClosedInitial, InitialBalance, ApprovedReferences

VARIABLES txns, postings, balances, idemResults, closedDates, heldCommands

vars == <<txns, postings, balances, idemResults, closedDates, heldCommands>>

TxnIds == {t.id : t \in txns}
CommandIds == TxnIds \cup {h.id : h \in heldCommands}
PostingTxnIds == {p.txn : p \in postings}
UsedKeys == {r.key : r \in idemResults}

DebitCount(acct) ==
  Cardinality({p \in postings : /\ p.account = acct /\ p.side = "DEBIT"})

CreditCount(acct) ==
  Cardinality({p \in postings : /\ p.account = acct /\ p.side = "CREDIT"})

TxnDebitCount(txn) ==
  Cardinality({p \in postings : /\ p.txn = txn /\ p.side = "DEBIT"})

TxnCreditCount(txn) ==
  Cardinality({p \in postings : /\ p.txn = txn /\ p.side = "CREDIT"})

Init ==
  /\ txns = {}
  /\ postings = {}
  /\ balances = InitialBalance
  /\ idemResults = {}
  /\ closedDates = ClosedInitial
  /\ heldCommands = {}

PostBalanced ==
  \E txn \in TxnSet, key \in KeySet, date \in DateSet,
     debitAcct \in AccountSet, creditAcct \in AccountSet:
    /\ txn \notin CommandIds
    /\ key \notin UsedKeys
    /\ date \notin closedDates
    /\ debitAcct # creditAcct
    /\ balances[debitAcct] > 0
    /\ txns' = txns \cup {[id |-> txn, status |-> "POSTED", original |-> "NONE", key |-> key, date |-> date, approval |-> "NONE"]}
    /\ postings' = postings \cup {
         [txn |-> txn, account |-> debitAcct, side |-> "DEBIT"],
         [txn |-> txn, account |-> creditAcct, side |-> "CREDIT"]
       }
    /\ balances' = [balances EXCEPT ![debitAcct] = @ - 1, ![creditAcct] = @ + 1]
    /\ idemResults' = idemResults \cup {[key |-> key, txn |-> txn]}
    /\ UNCHANGED <<closedDates, heldCommands>>

ReversePosted ==
  \E txn \in TxnSet, original \in TxnIds, key \in KeySet, date \in DateSet,
     originalDebitAcct \in AccountSet, originalCreditAcct \in AccountSet:
    /\ txn \notin CommandIds
    /\ key \notin UsedKeys
    /\ date \notin closedDates
    /\ \E t \in txns : /\ t.id = original /\ t.status = "POSTED"
    /\ [txn |-> original, account |-> originalDebitAcct, side |-> "DEBIT"] \in postings
    /\ [txn |-> original, account |-> originalCreditAcct, side |-> "CREDIT"] \in postings
    /\ balances[originalCreditAcct] > 0
    /\ txns' = txns \cup {[id |-> txn, status |-> "REVERSAL", original |-> original, key |-> key, date |-> date, approval |-> "NONE"]}
    /\ postings' = postings \cup {
         [txn |-> txn, account |-> originalCreditAcct, side |-> "DEBIT"],
         [txn |-> txn, account |-> originalDebitAcct, side |-> "CREDIT"]
       }
    /\ balances' = [balances EXCEPT ![originalCreditAcct] = @ - 1, ![originalDebitAcct] = @ + 1]
    /\ idemResults' = idemResults \cup {[key |-> key, txn |-> txn]}
    /\ UNCHANGED <<closedDates, heldCommands>>

PostApprovedAdjustment ==
  \E txn \in TxnSet, key \in KeySet, date \in DateSet, approval \in ApprovedReferences,
     debitAcct \in AccountSet, creditAcct \in AccountSet:
    /\ txn \notin CommandIds
    /\ key \notin UsedKeys
    /\ date \notin closedDates
    /\ debitAcct # creditAcct
    /\ balances[debitAcct] > 0
    /\ txns' = txns \cup {[id |-> txn, status |-> "ADJUSTMENT", original |-> "NONE", key |-> key, date |-> date, approval |-> approval]}
    /\ postings' = postings \cup {
         [txn |-> txn, account |-> debitAcct, side |-> "DEBIT"],
         [txn |-> txn, account |-> creditAcct, side |-> "CREDIT"]
       }
    /\ balances' = [balances EXCEPT ![debitAcct] = @ - 1, ![creditAcct] = @ + 1]
    /\ idemResults' = idemResults \cup {[key |-> key, txn |-> txn]}
    /\ UNCHANGED <<closedDates, heldCommands>>

HoldOrFailCommand ==
  \E command \in TxnSet, key \in KeySet:
    /\ command \notin CommandIds
    /\ key \notin UsedKeys
    /\ heldCommands' = heldCommands \cup {[id |-> command, key |-> key, status |-> "HELD_OR_FAILED"]}
    /\ idemResults' = idemResults \cup {[key |-> key, txn |-> command]}
    /\ UNCHANGED <<txns, postings, balances, closedDates>>

Next == PostBalanced \/ ReversePosted \/ PostApprovedAdjustment \/ HoldOrFailCommand

Spec == Init /\ [][Next]_vars

BalancedDoubleEntry ==
  \A t \in txns :
    t.status \in {"POSTED", "REVERSAL", "ADJUSTMENT"} => TxnDebitCount(t.id) = TxnCreditCount(t.id)

IdempotencySingleBusinessResult ==
  \A a \in idemResults :
    \A b \in idemResults :
      a.key = b.key => a.txn = b.txn

AvailableBalanceNonNegative ==
  \A acct \in AccountSet : balances[acct] >= 0

ReversalReferencesOriginal ==
  \A t \in txns :
    t.status = "REVERSAL" => \E original \in txns : /\ original.id = t.original /\ original.status = "POSTED"

ReversalMirrorsOriginal ==
  \A t \in txns :
    t.status = "REVERSAL" =>
      \E originalDebitAcct \in AccountSet, originalCreditAcct \in AccountSet :
        /\ [txn |-> t.original, account |-> originalDebitAcct, side |-> "DEBIT"] \in postings
        /\ [txn |-> t.original, account |-> originalCreditAcct, side |-> "CREDIT"] \in postings
        /\ [txn |-> t.id, account |-> originalCreditAcct, side |-> "DEBIT"] \in postings
        /\ [txn |-> t.id, account |-> originalDebitAcct, side |-> "CREDIT"] \in postings

ClosedDateNoDirectPosting ==
  \A t \in txns : t.date \notin closedDates

BalanceProjectionRecalculable ==
  \A acct \in AccountSet : balances[acct] = InitialBalance[acct] + CreditCount(acct) - DebitCount(acct)

HeldOrFailedCommandNoPosting ==
  \A h \in heldCommands : h.id \notin PostingTxnIds

AdjustmentRequiresApprovalReference ==
  \A t \in txns :
    t.status = "ADJUSTMENT" => t.approval \in ApprovedReferences

=============================================================================
