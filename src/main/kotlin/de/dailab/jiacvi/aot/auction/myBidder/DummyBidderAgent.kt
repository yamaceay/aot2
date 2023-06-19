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

    private var digest: Digest? = null
    private var privateValues: MutableMap<Pair<Item, Int>, Price> = mutableMapOf()
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

            publishInitialPrices(wallet!!)
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
            log.debug("Received {}", it)
            digest = it
            updatePrivateValues(wallet!!, digest!!)
        }

        listen<LookingFor>(biddersTopic) {
            log.debug("Received {}", it)
            askBestOffer(it.item)
        }

        // be notified of result of the entire auction
        on<AuctionResult> {
            log.info("Result of Auction: $it")
            wallet = null
        }
    }

    private fun askBestOffer(item: Item) {
        val price = privateValues.maxBy { it.value }?.value
            ?: (meanItemPrice(item, wallet!!)
                ?: return)

        val offer = Offer(id, secret, item, price)
        log.debug("Sending {}", offer)
        val ref = system.resolve(auctioneer)
        ref invoke ask<Boolean>(offer) { res ->
            log.debug("OfferAccepted: $res")
        }
    }
    private fun updatePrivateValues(wallet: Wallet, digest: Digest) {
        val deltas = listOf(-1.0, 1.0)
        for (walletItem in wallet.items) {

            val item = walletItem.key
            for (delta in deltas) {
                val marketPrice = medianItemPriceWithDigest(item, digest) ?: continue
                val count = delta.toInt()
                val preference = diffWallet(wallet, count, item, -delta * marketPrice)
                val itemDelta = Pair(item, count)
                privateValues[itemDelta] = preference
            }
        }
    }

    private fun medianItemPriceWithDigest(key: Item, digest: Digest): Price? {
        if (digest.itemStats.containsKey(key)) {
            val stats = digest.itemStats[key]!!
            return stats.median
        }
        return null
    }
    private fun publishInitialPrices(wallet: Wallet) {
        for (walletItem in wallet.items) {
            val item = walletItem.key
            val price = meanItemPrice(item, wallet)
            if (price != null) {
                val lookingFor = LookingFor(item, price)
                log.info("Sending $lookingFor")
                broker.publish(biddersTopic, lookingFor)
            }
        }
    }

    private fun meanItemPrice(key: Item, wallet: Wallet): Price? {
        if (wallet.items.containsKey(key)) {
            val itemCount = wallet.items[key]
            if (itemCount != null && itemCount != 0) {
                val itemValue = diffWallet(wallet, itemCount, key)
                return itemValue / itemCount
            }
        }
        return null
    }

    private fun diffWallet(wallet: Wallet, count: Int, item: Item, credits: Price = 0.0): Price {
        val oldValue = wallet.value()
        val walletWithoutItems = copyWallet(wallet)
        walletWithoutItems.update(item, -count, credits)
        val oldValueWithoutItems = walletWithoutItems.value()
        return (oldValue - oldValueWithoutItems).toDouble()
    }
    private fun copyWallet(wallet: Wallet): Wallet {
        return Wallet(wallet.bidderId, wallet.items.toMutableMap(), wallet.credits)
    }
}