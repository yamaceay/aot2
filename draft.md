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

### Implementation of Bidding Strategies of Agents

_For an intuitive explanation, refer to `biddingStrategies.md`._

Each agent uses the following information sources when bidding:
* `Number of Agents`
* `Wallet` is an attribute: For keeping track of the agent's credits and goods
* `LookingFor` is a type of message: For seeing the preferences of others
* `Digest` is a type of message: For seeing the statistical results of the last turn

```python

Agent = str
Good = str
GoodCount = dict[Good, int]

Credit = float 

Stats = tuple[
    int, # number of offers
    Credit, # min price
    Credit, # median price
    Credit, # max price
    ]

Wallet = tuple[GoodCount, Credit]
# - update(self, Good, int, Credit)
# - value(self) -> Credit

Offer = dict[Good, Credit]
Digest = dict[Good, Stats]

# Ensure that good type does not matter, except that each good is superadditive, this means: u(av + bv) >= u(av) + u(bv) for any v and any good type
def bidding_function(
    n_agents: int, 
    wallet: Wallet, 
    offers: dict[Agent, Offer], 
    digest: Digest
) -> Offer:
    any_agent_plays_strategically = False
    # False: Reactive, naive agent
    # True: Smarter, more strategic agent

    private_preferences = calculate_private_preferences(wallet)

    if any_agent_plays_strategically:
        public_preferences_digest = calculate_public_preferences(offers)
        digest = consider_bluffs(
            digest,
            public_preferences_digest,
    )

    public_preferences = sorted(digest, key=lambda x: digest[x][2], reverse=True)
    
    strategy = develop_strategy(
        private_preferences, 
        public_preferences,
    )
    return strategy

# Estimate the private preferences by differentiating the new wallet with one more good for a specific type and the old wallet
def calculate_private_preferences(wallet: Wallet) -> dict[Good, Credit]:
    private_preferences = {}
    goods, credits = wallet
    for good, count in goods.items():
        old_value = value(wallet)

        # Generate a new copy of wallet with one more good of given type
        goods_copy = goods.copy()
        goods_copy[good] = count + 1
        wallet_copy = (goods_copy, credits)

        # Save the preference value
        preference = value(wallet_copy) - old_value
        private_preferences[good] = preference

    private_preferences = sorted(private_preferences, key=lambda x: private_preferences[x], reverse=True)

    return private_preferences

def calculate_public_preferences(offers: dict[Agent, Offer]) -> dict[Good, Credit]:
    public_preferences = {}
    for agent, offer in offers.items():
        for good, price in offer.items():
            if good not in public_preferences:
                public_preferences[good] = []
            public_preferences[good] += [price]

    public_preferences_digest = {
        k: (
            len(v), 
            min(v), 
            median(v), 
            max(v)
        ) for k, v in public_preferences.items()}

    return public_preferences

def develop_strategy(
    private_preferences: list[Good], 
    public_preferences: list[Good],
) -> Offer:
    raise Exception("Not implemented")

def consider_bluffs(
    digest: Digest,
    public_preferences_digest: Digest,
) -> Digest:
    raise Exception("Not implemented")
```