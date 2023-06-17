`Main.kt`

AuctioneerAgent:
* Max. Turns: 20
* Max. Turns w/o Offer: 5
* Turn Seconds: 1

DummyBidderAgents:
* Number of Agents: 4

`AuctioneerAgent.kt`

Phases:
* REGISTERING: Agents register, registerPhase()
    * Broadcast all bidders with a register message, including the logistics of the auction
* STARTING: After registration, startPhase()
    * Assign bidders 500 credits worth of items of random types
    * Broadcast a registered message with the initial items and credits
* BIDDING: Agents bid on items, biddingPhase()
    * Get all already advertised offers where bid-lists exist
    * Sort the bids in descending order
    * Determine the final price
    * Send messages to both seller and buyer (if any)
    * Advertise any new Offers to the bidder agents
* EVALUATING: Auctioneer evaluates all bids, evaluationPhase()
    * Sort all offers by bid
    * Send the AuctionResults to all bidders, keeping this order
* END: End of Auction, -

* Act 
    * On CashIn messages:
        * Check if a cash-in is valid
        * Update the bidder's wallet
        * Send a CashInResult message to the bidder
    * On Offer messages:
        * Check if an offer is valid
        * Calculate how much the bidder is already expected to pay (pending)
        * If the bid and pending amount is less than the bidder's credits, save the offer 

Attributes:
* Secrets: Random secret numbers used for each bidder for very simple authentication in both sides
* Wallets: For keeping track of bidder agents' wallets (they should do the same)
* Pools: Current "pools" of items to change their owner, grouped by item type

`DummyBidderAgent.kt`

```kotlin
DummyBidderAgent:
- broker: BrokerAgentRef
- wallet: Wallet?
- secret: Int

+ behaviour(): Unit
    + listen<StartAuction>(biddersTopic): Unit
    + listen<Registered>(biddersTopic): Unit
    + listen<Digest>(biddersTopic): Unit
    + listen<LookingFor>(biddersTopic): Unit
    + listen<AuctionResult>(biddersTopic): Unit
    + listen<OfferResult>(biddersTopic): Unit
```