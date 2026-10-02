package edu.cit.sanico.channel;

/**
 * Disabled in favor of explicit, sequenced stock synchronization in:
 * 1. TianggeFeedPoller (after ACCEPTED decision & after cancellation confirmation)
 * 2. BackorderResolutionListener (after backorder ACCEPTED resolution)
 * 3. AutoReorderListener (after supplier restock delivery)
 *
 * This ensures Tiangge receives PUT /stock strictly AFTER the associated decision,
 * completely eliminating "updates that ignored an accepted order" and "stock not decremented".
 */
class StockSyncEventListener {
}
