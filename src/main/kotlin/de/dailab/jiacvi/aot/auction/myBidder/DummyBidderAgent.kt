package de.dailab.jiacvi.aot.auction.myBidder

import de.dailab.jiacvi.aot.auction.*
import de.dailab.jiacvi.Agent
import de.dailab.jiacvi.BrokerAgentRef
import de.dailab.jiacvi.behaviour.act
import java.util.PriorityQueue
import kotlin.math.min

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
    private var acceptedOffers: MutableMap<Item, PriorityQueue<Triple<Price, Int, Price>>> = mutableMapOf()
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

            for (walletItem in wallet!!.items) {
                val price = getPrice(walletItem.key)
                if (price != null) {
                    publishLookingFor(walletItem.key, price)
                }
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
            val delta = when (it.transfer) {
                Transfer.SOLD -> -1
                Transfer.BOUGHT -> +1
                else -> 0
            }
            if (!acceptedOffers.containsKey(it.item) || acceptedOffers[it.item] == null) {
                acceptedOffers[it.item] = PriorityQueue<Triple<Price, Int, Price>>(
                    compareBy { offer -> offer.first }
                )
            }

            val value = diffWallet(wallet!!, delta, it.item, it.price)
            acceptedOffers[it.item]!!.add(Triple(value, delta, it.price))
        }

        listen<Digest>(biddersTopic) {
            log.debug("Received {}", it)
            digest = it

            for (entry in acceptedOffers.entries) {
                val item = entry.key
                val numItems = entry.value.sumBy { offer -> offer.second }
                val meanPrice = entry.value.sumByDouble { offer -> offer.third } / entry.value.size
                val creditGain = - numItems * meanPrice
                log.debug("Item: {}, numItems: {}, creditGain: {}", item, numItems, creditGain)

                val price = getPrice(item)
                if (price != null) {
                    publishLookingFor(item, price)
                }
            }
            acceptedOffers.clear()
        }

        listen<LookingFor>(biddersTopic) {
            log.debug("Received {}", it)
            val price = getPrice(it.item)
            if (price != null) {
                askOffer(it.item, price)
            }
        }

        // be notified of result of the entire auction
        on<AuctionResult> {
            log.info("Result of Auction: $it")

            for (walletItem in wallet!!.items) {
                val item = walletItem.key
                val count = walletItem.value
                val cashIn = CashIn(id, secret, item, count)
                log.debug("CashIn {}", cashIn)

                val ref = system.resolve(auctioneer)
                ref invoke ask<CashInResult>(cashIn) { res ->
                    log.debug("CashInResult: {}", res)
                }
            }
            wallet = null
        }
    }

    private fun askOffer(item: Item, price: Price) {
        val offer = Offer(id, secret, item, price)
        log.debug("Sending {}", offer)
        val ref = system.resolve(auctioneer)
        ref invoke ask<Boolean>(offer) { res ->
            log.debug("OfferAccepted: $res")
        }
    }

    private fun publishLookingFor(item: Item, price: Price) {
        val lookingFor = LookingFor(item, price)
        log.info("Sending $lookingFor")
        broker.publish(biddersTopic, lookingFor)
    }

    private fun getPrice(item: Item): Price? {
        val marketPrice = medianPriceFromDigest(item, digest)
        val gains = maximumGain(wallet!!, item, marketPrice)
        val gain = gains.maxBy { it.value }
        if (gain != null) {
            val count = gain.key.toDouble()
            return marketPrice + count * gain.value
        }
        return null
    }
    private fun maximumGain(wallet: Wallet, item: Item, marketPrice: Price): MutableMap<Int, Price> {
        val privateValue = mutableMapOf<Int, Price>()
        val totalCount = wallet.items[item] ?: 0
        val deltas = listOf(-1.0, 1.0)
        for (delta in deltas) {
            val count = delta.toInt()
            if (count < min(-totalCount, 0)) continue
            val preference = diffWallet(wallet, count, item, -delta * marketPrice)
            privateValue[count] = if (preference <= 0 && count < 0 ) 2 * count * preference else preference
        }
        return privateValue
    }

    private fun medianPriceFromDigest(key: Item, digest: Digest?): Price {
        if (digest != null && digest.itemStats.containsKey(key)) {
            val stats = digest.itemStats[key]!!
            return stats.median
        }
        return 0.0
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