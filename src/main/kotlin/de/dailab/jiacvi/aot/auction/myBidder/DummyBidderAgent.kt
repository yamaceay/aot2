package de.dailab.jiacvi.aot.auction.myBidder

import de.dailab.jiacvi.aot.auction.*
import de.dailab.jiacvi.Agent
import de.dailab.jiacvi.BrokerAgentRef
import de.dailab.jiacvi.behaviour.act
import kotlin.math.pow
import kotlin.math.sqrt

class DummyBidderAgent(private val id: String): Agent(overrideName=id) {
    // you can use the broker to broadcast messages i.e. broker.publish(biddersTopic, LookingFor(...))
    private val broker by resolve<BrokerAgentRef>()

    // keep track of the bidder agent's own wallet
    private var wallet: Wallet? = null
    private var secret: Int = -1

    private var digest: Digest? = null
    private var rivalBids: MutableMap<Item, MutableList<Price>> = mutableMapOf()

    private val explorationRate: Double = 0.5
    private val punishFactor: Double = 2.0
    private val offerThreshold: Double = 0.9
    enum class Delta {
        SELL, STAY, BUY;
        fun toInt(): Int {
            return when (this) {
                SELL -> -1
                STAY -> 0
                BUY -> 1
            }
        }
    }
    override fun behaviour() = act {
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
                if (price <= 0.0) {
                    throw Exception("Price should be positive")
                }

                val lookingFor = LookingFor(walletItem.key, price)
                log.info("Sending $lookingFor")
                broker.publish(biddersTopic, lookingFor)
            }
        }

        listen<LookingFor>(biddersTopic) {
            log.debug("Received {}", it)

            rivalBids.putIfAbsent(it.item, mutableListOf())
            rivalBids[it.item]!!.add(it.price)

            val offerPair = getPriceOnLookingFor(it.item)
            if (offerPair.second) {
                val price = offerPair.first
                if (price <= 0.0) {
                    throw Exception("Price should be positive")
                }

                val offer = Offer(id, secret, it.item, price)
                log.debug("Sending {}", offer)
                val ref = system.resolve(auctioneer)
                ref invoke ask<Boolean>(offer) { res ->
                    log.debug("OfferAccepted: $res")
                }
            }
        }

        // be notified of result of own offer
        on<OfferResult> {
            log.info("Result for my Offer: $it")
            when (it.transfer) {
                Transfer.SOLD -> wallet?.update(it.item, -1, +it.price)
                Transfer.BOUGHT -> wallet?.update(it.item, +1, -it.price)
                else -> {}
            }
            while ((wallet?.credits ?: 0.0) < 0.0) {
                if (wallet?.items?.isEmpty() != false) break
                val itemToSell = selectItemToSell()
                val cashIn = CashIn(id, secret, itemToSell, 1)
                log.debug("CashIn {}", cashIn)

                val ref = system.resolve(auctioneer)
                ref invoke ask<CashInResult>(cashIn) { res ->
                    log.debug("CashInResult: {}", res)
                }
            }
        }

        listen<Digest>(biddersTopic) {
            log.debug("Received {}", it)
            digest = it

            for (item in it.itemStats.keys) {
                val price = getPriceOnDigest(item)
                if (price <= 0.0) {
                    throw Exception("Price should be positive")
                }

                val lookingFor = LookingFor(item, price)
                log.info("Sending $lookingFor")
                broker.publish(biddersTopic, lookingFor)

            }
            rivalBids = mutableMapOf()
        }

        // be notified of result of the entire auction
        on<AuctionResult> {
            log.info("Result of Auction: $it")
            wallet = null
        }
    }

    private fun getPriceOnRegistered(item: Item): Price {
        return scores(item).values.min()!!
    }

    private fun getPriceOnLookingFor(item: Item): Pair<Price, Boolean> {
        val marketPrice = median(rivalBids[item]!!)
        val buyValue = score(item, Delta.BUY, marketPrice)
        val sellValue = score(item, Delta.SELL, marketPrice)
        val shiftFactor = sqrt(buyValue / sellValue)
        val shiftMatters = shiftFactor / offerThreshold <= 1 || shiftFactor * offerThreshold >= 1
        return Pair(marketPrice * shiftFactor, shiftMatters)
    }

    private fun getPriceOnDigest(item: Item): Price {
        val myFakedPrice = scores(item).values.min()!!
        val theirFakedPrice = median(rivalBids[item]!!)
        val marketPrice = digest!!.itemStats[item]!!.median
        val fakeFactor = sqrt(myFakedPrice / theirFakedPrice)
        return fakeFactor * marketPrice
    }

    private fun selectItemToSell(): Item {
        return if (Math.random() < explorationRate) wallet!!.items.keys.random()
        else wallet!!.items.maxBy { it.value }!!.key
    }

    private fun scores(item: Item, price: Price = 0.0): MutableMap<Delta, Price> {
        return mutableMapOf(
            Delta.SELL to score(item, Delta.SELL, price),
            Delta.BUY to score(item, Delta.BUY, price)
        )
    }

    private fun score(item: Item, want: Delta, marketPrice: Price = 0.0): Price {
        if (wallet == null) throw IllegalStateException("Wallet is not initialized")

        val difference = diffWallet(wallet!!, item, want.toInt(), -want.toInt() * marketPrice)
        var preference = want.toInt() * difference
        if (!wallet!!.items.containsKey(item) || wallet!!.items[item]!! < 0) {
            if (want === Delta.SELL) preference *= punishFactor
        }
        return preference
    }
    private fun diffWallet(wallet: Wallet, item: Item, count: Int, credits: Price = 0.0): Price {
        val oldValue = wallet.value()
        val walletWithoutItems = Wallet(wallet.bidderId, wallet.items.toMutableMap(), wallet.credits)
        walletWithoutItems.update(item, -count, credits)
        val oldValueWithoutItems = walletWithoutItems.value()
        return (oldValue - oldValueWithoutItems).toDouble()
    }
}