package de.dailab.jiacvi.aot.auction.myBidder

import de.dailab.jiacvi.aot.auction.*
import de.dailab.jiacvi.Agent
import de.dailab.jiacvi.BrokerAgentRef
import de.dailab.jiacvi.behaviour.act

/**
 * This is a simple stub of the Bidder Agent. You can use this as a template to start your implementation.
 */
class DummyBidderAgent(private val id: String): Agent(overrideName=id) {
    // you can use the broker to broadcast messages i.e. broker.publish(biddersTopic, LookingFor(...))
    private val broker by resolve<BrokerAgentRef>()

    // keep track of the bidder agent's own wallet
    private var wallet: Wallet? = null
    private var secret: Int = -1
    override fun behaviour() = act {
        // TODO implement your bidding strategie. When to offer (buy/sell) items
        //  at which price. You can also use the cashin function if you need money.

        // register to all started auctions
        listen<StartAuction>(biddersTopic) {
            val message = Register(id)
            log.info("Received $it, Sending $message")

            val ref = system.resolve(auctioneer)
            ref invoke ask<Boolean>(message) { res ->
                log.info("Registered: $res")
            }
        }

        // handle Registered message, initialize Wallet
        on<Registered> {
            wallet = Wallet(id, it.items.toMutableMap(), it.credits)
            secret = it.secret
            log.info("Initialized Wallet: $wallet, secret: $secret")

            val walletCopy = wallet!!.copy()

            for (walletItem in walletCopy.items) {
                val item = walletItem.key
                val price: Price = meanItemPrice(item, walletCopy) ?: continue
                val lookingFor = LookingFor(item, price)

                log.info("Sending $lookingFor")
                broker.publish(biddersTopic, lookingFor)
            }
        }

        // be notified of result of own offer
        on<OfferResult> {
            log.info("Result for my Offer: $it")
            when (it.transfer) {
                Transfer.SOLD   -> wallet?.update(it.item, -1, +it.price)
                Transfer.BOUGHT -> wallet?.update(it.item, +1, -it.price)
                else -> {}
            }
        }

        listen<Digest>(biddersTopic) {
            log.debug("Received Digest: {}", it)
        }

        listen<LookingFor>(biddersTopic) {
            log.debug("Received LookingFor: {}", it)
        }

        // be notified of result of the entire auction
        on<AuctionResult> {
            log.info("Result of Auction: $it")
            wallet = null
        }
    }

    private fun meanItemPrice(key: Item, wallet: Wallet): Price? {
        if (wallet.items.containsKey(key)) {
            val itemCount = wallet.items[key]
            if (itemCount != null && itemCount != 0) {
                val oldValue = wallet.value()

                val walletCopyWithoutItems = wallet.copy()
                walletCopyWithoutItems.update(key, -itemCount, 0.0)
                val oldValueWithoutItems = walletCopyWithoutItems.value()

                val itemValue = (oldValue - oldValueWithoutItems).toDouble()
                return itemValue / itemCount
            }
        }
        return null
    }
}