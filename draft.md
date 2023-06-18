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
* `Digest` is a type of message: For seeing the statistical results of the last turn / and also market place prices
* `LookingFor` is a type of message: For seeing the preferences of others

```python

Agent = str
Good = str
GoodCount = tuple[Good, int]
Inventory = dict[Good, int]

Credit = float 

Stats = tuple[
    int, # number of offers
    Credit, # min price
    Credit, # median price
    Credit, # max price
    ]

Wallet = tuple[Inventory, Credit]
# - update(self, Good, int, Credit)
# - value(self) -> Credit

Offer = dict[Good, Credit]
Digest = dict[Good, Stats]

# Ensure that good type does not matter, except that each good is superadditive, this means: u(av + bv) >= u(av) + u(bv) for any v and any good type

# any_agent_plays_strategically: If True, then the agent will try to play strategically, otherwise it will play honestly
def bidding_function(
    n_agents: int, 
    wallet: Wallet, 
    offers: dict[Agent, Offer], 
    digest: Digest, 
    any_agent_plays_strategically: bool = False, 
) -> Offer:

    private_preferences = calculate_private_preferences(wallet, digest)

    public_preferences = None
    if any_agent_plays_strategically:
        public_preferences = calculate_public_preferences(offers)

    strategy = develop_strategy(
        private_preferences, 
        public_preferences,
        digest,
    )
    return strategy

# Estimate the private preferences by differentiating the new wallet with one more good for a specific type and the old wallet
def calculate_private_preferences(wallet: Wallet, digest: Digest) -> dict[GoodCount, Credit]:
    goods, credits = wallet
    old_value = value(wallet)

    private_preferences = {}

    for good, count in goods.items():

        # Delta is either -1 (sell) or 1 (buy)
        # If delta is -1, then new credit would be +current_price = -1 * (-1 * current_price)
        # If delta is 1, then new credit would be -current_price = -1 * (1 * current_price)
        for delta in [-1, 1]:
            current_price = digest[good][2] # median price

            wallet_copy = wallet.copy()
            
            update(wallet_copy, good, count + delta, -delta * current_price)

            # Save the preference value
            preference = value(wallet_copy) - old_value

            private_preferences[(good, delta)] = preference

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
    private_preferences: dict[GoodCount, Credit],
    public_preferences: dict[Good, Stats],
    digest: Digest,
) -> Offer:
    offer = {}

    private_preferences_reshaped = {}
    for (good, count), private_value in private_preferences.items():
        if good not in private_preferences_reshaped:
            private_preferences_reshaped[good] = {}
        private_preferences_reshaped[good][count] = private_value

    for good, count_private_value in private_preferences_reshaped:
        n_actual_agents, _, actual_price, _ = digest[good]

        if public_preferences is None or good not in public_preferences:
            count = max(count_private_value, key=lambda x: count_private_value[x])
            private_value = count_private_value[count]

            # If selling pays off or buying does not, then bid should be lower
            # If buying pays off or selling does not, then bid should be higher
            bid = actual_price + count * private_value
            offer[good] = bid
            
            continue
        
        n_smart_agents, _, bluffed_price, _ = public_preferences[good]

        # TODO: Implement (see my heuristic below)
    return offer
```

#### My Heuristic

Preference of other agents:

* If the bluffed price is below the actual price, then agents are in general more willing to buy
* If the bluffed price is above the actual price, then agents are in general more willing to sell

Preference of this agent:

Now consider the cases if this agent wants to buy / sell: private values are already calculated

If the number of smart agents is very high: Preferences of other agents are more important than the preferences of this agent

We can realize this by calculating the weighted sum of the bluffed price and the actual price like this:
* $\frac{\text{nSmartAgents} * \text{bluffedPrice} + \text{nActualAgents} * \text{actualPrice}}{\text{nSmartAgents} + \text{nActualAgents}}$

Assuming the number of smart agents is high, then the following cases can happen:

- [COMPETITIVE] I buy / y'all buy: Remember the strategic decision of subtracting $price / n_smart_agents$ from the bid

- [SAFE] I buy / y'all sell: Adding $price / n_smart_agents$ to the bid would help keep the price low

Similarly, for selling:

- [COMPETITIVE] I sell / y'all sell: Add $price / n_smart_agents$ to the bid

- [SAFE] I sell / y'all buy: Subtract $price / n_smart_agents$ from the bid