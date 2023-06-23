package de.dailab.jiacvi.aot.auction.myBidder

import de.dailab.jiacvi.aot.auction.*
import de.dailab.jiacvi.Agent
import de.dailab.jiacvi.BrokerAgentRef
import de.dailab.jiacvi.behaviour.act
import java.util.PriorityQueue
import kotlin.math.max

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
    private var rivalBids: MutableMap<Item, MutableList<Price>> = mutableMapOf()
    private var acceptedOffers: MutableMap<Item, PriorityQueue<Triple<Price, Delta, Price>>> = mutableMapOf()
    private var priceChange: Double = 1.0 // 0: Trust, 1: Distrust

    enum class Delta {
        SELL, STAY, BUY;
        fun toInt(): Int {
            return when (this) {
                SELL -> 1
                STAY -> 0
                BUY -> -1
            }
        }
    }
    fun toDelta(int: Int): Delta {
        return when (int) {
            1 -> Delta.BUY
            -1 -> Delta.SELL
            else -> Delta.STAY
        }
    }
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
                val price = getPriceOnRegistered(walletItem.key)
                if (price >= 0.0) {
                    publishLookingFor(walletItem.key, price)
                }
            }
        }

        listen<LookingFor>(biddersTopic) {
            log.debug("Received {}", it)

            rivalBids.putIfAbsent(it.item, mutableListOf())
            rivalBids[it.item]!!.add(it.price)

            val price = getPriceOnLookingFor(it.item);
            if (price >= 0.0) {
                askOffer(it.item, price)
            }
        }

        // be notified of result of own offer
        on<OfferResult> {
            log.info("Result for my Offer: $it")
            val delta = when (it.transfer) {
                Transfer.SOLD   -> { wallet?.update(it.item, -1, +it.price); -1
                }

                Transfer.BOUGHT -> { wallet?.update(it.item, +1, -it.price); +1
                }

                else -> 0
            }
            if (delta != 0) {
                acceptedOffers.putIfAbsent(it.item, PriorityQueue(
                    compareBy { offer -> offer.first }
                ))

                val value = diffWallet(wallet!!, it.item, delta, it.price)
                acceptedOffers[it.item]!!.add(Triple(value, toDelta(delta), it.price))
            }
        }

        listen<Digest>(biddersTopic) {
            log.debug("Received {}", it)
            digest = it

            for (entry in acceptedOffers.entries) {
                val item = entry.key
                val numItems = entry.value.sumBy { offer -> offer.second.toInt() }
                val meanPrice = entry.value.sumByDouble { offer -> offer.third } / entry.value.size
                val creditGain = - numItems * meanPrice
                log.debug("Item: {}, numItems: {}, creditGain: {}", item, numItems, creditGain)

                val price = getPriceOnDigest(item)
                if (price >= 0.0) {
                    publishLookingFor(item, price)
                }
            }
            rivalBids = mutableMapOf()
            acceptedOffers.clear()
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

    // Measure the difference in wallet value if the item count is changed by delta
    // The price is also the difference
    private fun getPriceOnRegistered(item: Item): Price {
        val delta = gain(item)
        return score(item, delta)
    }

    // Assume the market price is the median of the rival bids
    // If trusted, the market price is equal to the median of the rival bids -> Normal stakes
    // If not trusted, the median of the rival bids is different from the real market price
    //      If the median is higher than the real market price, the players want to sell -> Buy to them -> Lower stakes
    //      If the median is lower than the real market price, the players want to buy -> Sell to them -> Higher stakes
    private fun getPriceOnLookingFor(item: Item): Price {
        val price = median(rivalBids[item]!!)
        return getPrice(item, price)
    }

    private fun getPriceOnDigest(item: Item): Price {
        val predictedPrice = median(rivalBids[item]!!)
        val actualPrice = digest!!.itemStats[item]!!.median
        priceChange = 1 - predictedPrice / actualPrice
        if (priceChange < 0) priceChange = -priceChange
        log.debug("PredictedPrice: {}, ActualPrice: {}", predictedPrice, actualPrice)
        log.debug("PriceChange: {}", priceChange)
        return getPrice(item, actualPrice)
    }

    private fun getPrice(item: Item, price: Price): Price {
        val delta = gain(item, price)
        val diff = score(item, delta, price)
        val sign = priceChange * delta.toInt()
        return price + sign * diff
    }
    private fun gain(item: Item, price: Price = 0.0): Delta {
        val sellDiff = score(item, Delta.SELL, price)
        val buyDiff = score(item, Delta.BUY, price)
        val maxDiff = max(sellDiff, buyDiff)
        return if (maxDiff > 0) if (maxDiff == buyDiff) Delta.BUY else Delta.SELL else Delta.STAY
    }

    private fun score(item: Item, want: Delta, marketPrice: Price = 0.0): Price {
        if (wallet == null) {
            throw IllegalStateException("Wallet is not initialized")
        }
        var transferAmount = -want.toInt() * marketPrice
        if (wallet!!.items[item] == null) {
            transferAmount *= 2
        }

        return diffWallet(wallet!!, item, want.toInt(), transferAmount)
    }
    private fun diffWallet(wallet: Wallet, item: Item, count: Int, credits: Price = 0.0): Price {
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