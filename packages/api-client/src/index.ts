/**
 * Backward-compatible public barrel.
 *
 * New channel code should prefer the domain entry points exported by the
 * package (`/customer`, `/call-center`, `/staff`, `/risk`, `/operations`).
 */
export * from "./client";
export * from "./payment-settlement";
