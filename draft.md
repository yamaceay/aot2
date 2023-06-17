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

```yaml
DummyBidderAgent:
- broker: BrokerAgentRef
- wallet: Wallet?
- secret: Int

Receives:
# - [DONE] StartAuction from topic "all-bidders"
# - [EASY] Registered
- LookingFor from topic "all-bidders"
- OfferResult
- Digest from topic "all-bidders"
# - [EASY] AuctionResult

Sends:
# - [DONE] Register
- LookingFor to topic "all-bidders"
- Offer
# - [EASY] CashIn
```

Each agent uses the following information sources when bidding:
* `Number of Agents`
* `Wallet` is an attribute: For keeping track of the agent's credits and goods
* `LookingFor` is a type of message: For seeing the preferences of others
* `Digest` is a type of message: For seeing the statistical results of the last turn

```python

Stats = tuple[
    int, # number of offers
    Price, # min price
    Price, # median price
    Price, # max price
    ]

Agent = str
Good = str
GoodOrCredit = str

Amount = float
Price = float
PriceCount = tuple[Price, int]

Wallet = dict[GoodOrCredit, Amount]
# - update()
# - value()

Digest = dict[Good, Stats]
Offer = dict[Good, PriceCount]

def bidding_function(
    n_agents: int, 
    wallet: Wallet, 
    offers: dict[Agent, Offer], 
    digest: Digest = None
) -> Offer:
    # TODO: implement
```