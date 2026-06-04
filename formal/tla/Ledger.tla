----------------------------- MODULE Ledger -----------------------------
EXTENDS Naturals, Integers, FiniteSets

\* Concepts covered: Accounts, Transactions, Postings, Debit/Credit,
\* Balance projection, IdempotencyKey, Reversal, Closed business date,
\* and held/failed transfers that must not produce ledger postings.

CONSTANTS AccountSet, TxnSet, KeySet, DateSet, ClosedInitial

VARIABLES txns, postings, balances, idemResults, closedDates, heldTransfers

vars == <<txns, postings, balances, idemResults, closedDates, heldTransfers>>

TxnIds == {t.id : t \in txns}
PostingTxnIds == {p.txn : p \in postings}

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
  /\ balances = [acct \in AccountSet |-> 0]
  /\ idemResults = {}
  /\ closedDates = ClosedInitial
  /\ heldTransfers = {}

PostBalanced ==
  \E txn \in TxnSet, key \in KeySet, date \in DateSet,
     debitAcct \in AccountSet, creditAcct \in AccountSet:
    /\ txn \notin TxnIds
    /\ key \notin {r.key : r \in idemResults}
    /\ date \notin closedDates
    /\ debitAcct # creditAcct
    /\ txns' = txns \cup {[id |-> txn, status |-> "POSTED", original |-> "NONE", key |-> key, date |-> date]}
    /\ postings' = postings \cup {
         [txn |-> txn, account |-> debitAcct, side |-> "DEBIT"],
         [txn |-> txn, account |-> creditAcct, side |-> "CREDIT"]
       }
    /\ balances' = [balances EXCEPT ![debitAcct] = @ - 1, ![creditAcct] = @ + 1]
    /\ idemResults' = idemResults \cup {[key |-> key, txn |-> txn]}
    /\ UNCHANGED <<closedDates, heldTransfers>>

ReversePosted ==
  \E txn \in TxnSet, original \in TxnIds, key \in KeySet, date \in DateSet,
     debitAcct \in AccountSet, creditAcct \in AccountSet:
    /\ txn \notin TxnIds
    /\ key \notin {r.key : r \in idemResults}
    /\ date \notin closedDates
    /\ \E t \in txns : /\ t.id = original /\ t.status = "POSTED"
    /\ debitAcct # creditAcct
    /\ txns' = txns \cup {[id |-> txn, status |-> "REVERSAL", original |-> original, key |-> key, date |-> date]}
    /\ postings' = postings \cup {
         [txn |-> txn, account |-> debitAcct, side |-> "DEBIT"],
         [txn |-> txn, account |-> creditAcct, side |-> "CREDIT"]
       }
    /\ balances' = [balances EXCEPT ![debitAcct] = @ - 1, ![creditAcct] = @ + 1]
    /\ idemResults' = idemResults \cup {[key |-> key, txn |-> txn]}
    /\ UNCHANGED <<closedDates, heldTransfers>>

HoldOrFailTransfer ==
  \E txn \in TxnSet, key \in KeySet:
    /\ txn \notin TxnIds
    /\ key \notin {r.key : r \in idemResults}
    /\ heldTransfers' = heldTransfers \cup {[txn |-> txn, key |-> key, status |-> "HELD_OR_FAILED"]}
    /\ idemResults' = idemResults \cup {[key |-> key, txn |-> txn]}
    /\ UNCHANGED <<txns, postings, balances, closedDates>>

Next == PostBalanced \/ ReversePosted \/ HoldOrFailTransfer

Spec == Init /\ [][Next]_vars

BalancedDoubleEntry ==
  \A t \in txns :
    t.status \in {"POSTED", "REVERSAL"} => TxnDebitCount(t.id) = TxnCreditCount(t.id)

IdempotencySingleBusinessResult ==
  \A a \in idemResults :
    \A b \in idemResults :
      a.key = b.key => a.txn = b.txn

ReversalReferencesOriginal ==
  \A t \in txns :
    t.status = "REVERSAL" => \E original \in txns : /\ original.id = t.original /\ original.status = "POSTED"

ClosedDateNoDirectPosting ==
  \A t \in txns : t.date \notin closedDates

BalanceProjectionRecalculable ==
  \A acct \in AccountSet : balances[acct] = CreditCount(acct) - DebitCount(acct)

HeldOrFailedTransferNoPosting ==
  \A h \in heldTransfers : h.txn \notin PostingTxnIds

=============================================================================
