package de.dailab.jiacvi.aot.auction.myBidder

import de.dailab.jiacvi.aot.auction.*
import de.dailab.jiacvi.Agent
import de.dailab.jiacvi.BrokerAgentRef
import de.dailab.jiacvi.behaviour.act
import kotlin.math.max
import kotlin.math.sqrt

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
                    val lookingFor = LookingFor(walletItem.key, price)
                    log.info("Sending $lookingFor")
                    broker.publish(biddersTopic, lookingFor)
                }
            }
        }

        listen<LookingFor>(biddersTopic) {
            log.debug("Received {}", it)

            rivalBids.putIfAbsent(it.item, mutableListOf())
            rivalBids[it.item]!!.add(it.price)

            val price = getPriceOnLookingFor(it.item);
            if (price >= 0.0) {
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
        }

        listen<Digest>(biddersTopic) {
            log.debug("Received {}", it)
            digest = it

            for (item in it.itemStats.keys) {
                val price = getPriceOnDigest(item)
                if (price >= 0.0) {
                    val lookingFor = LookingFor(item, price)
                    log.info("Sending $lookingFor")
                    broker.publish(biddersTopic, lookingFor)
                }
            }
            rivalBids = mutableMapOf()
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

    // Strategy #1: If the buying is more lucrative than selling
    //   send the selling price to others, otherwise send the buying price
    // Reason: Lower the prices if trying to buy and vice versa
    private fun getPriceOnRegistered(item: Item): Price {
        val fake = scores(item).minBy { it.value } !!
        val myFakedAction = fake.key
        val myFakedPrice = fake.value

        // Debugging
        log.debug("Faking to {} {} for {}", myFakedAction, item, myFakedPrice)
        return myFakedPrice
    }

    // Strategy #1: Assume that the market price is the median of the rival bids
    //   and the private preference is calculated by using the rival price
    //   because we don't have another choice or information source to check if
    //   the rivals are bluffing or not
    // Return the price of action which is more lucrative
    private fun getPriceOnLookingFor(item: Item): Price {
        val marketPrice = median(rivalBids[item]!!)
        val real = scores(item, marketPrice).maxBy { it.value } !!
        val myRealAction = real.key
        val myRealPrice = real.value

        // Debugging
        log.debug("Actually wanting to {} {} for {}", myRealAction, item, myRealPrice)
        return myRealPrice
    }

    // Strategy #1: Assume that now we have a valid market price and the
    //   rival price too. Similar as in getPriceOnRegistered, we can fake
    //   the image of selling it, whereas we want in fact to buy it (or vice versa)
    //   Assuming the rival is
    private fun getPriceOnDigest(item: Item): Price {
        val fake = scores(item).minBy { it.value } !!
        val myFakedAction = fake.key
        val myFakedPrice = fake.value

        val theirPublicPrice = median(rivalBids[item]!!)
        val marketPrice = digest!!.itemStats[item]!!.median

        // Rival wants to buy: 8 / 5 = 5 / 3 = 3 / 2 = ... golden ratio
        // Rival wants to sell: 3 / 5 = 2 / 3 = ... inverse of golden ratio
        // marketPrice ** 2 / theirPublicPrice = theirActualPrice
        // Making their actual prices public -> faking their opposite tendency
        val theirActualPrice = marketPrice * marketPrice / theirPublicPrice
        val actualRivalTendency = if (theirActualPrice > theirPublicPrice) Delta.BUY else Delta.SELL
        val geometricMeanOfBluffedPrices = sqrt(myFakedPrice * theirActualPrice)

        // Debugging
        log.debug("Rival wants to {} at {}", actualRivalTendency, theirActualPrice)
        log.debug("I want to fake my private preference and {} at {}", myFakedAction, marketPrice)
        log.debug("My best price range: between {} and {}", myFakedPrice, theirActualPrice)
        log.debug("So my best price is: {}", geometricMeanOfBluffedPrices)
        return geometricMeanOfBluffedPrices
    }

    private fun scores(item: Item, price: Price = 0.0): MutableMap<Delta, Price> {
        return mutableMapOf(
            Delta.SELL to score(item, Delta.SELL, price),
            Delta.BUY to score(item, Delta.BUY, price)
        )
    }

    private fun score(item: Item, want: Delta, marketPrice: Price = 0.0): Price {
        if (wallet == null) {
            throw IllegalStateException("Wallet is not initialized")
        }
        var transferAmount = -want.toInt() * marketPrice
        if (!wallet!!.items.containsKey(item) || wallet!!.items[item]!! < want.toInt()) {
            transferAmount *= 2
        }

        val difference = diffWallet(wallet!!, item, want.toInt(), transferAmount)
        return want.toInt() * difference
    }
    private fun diffWallet(wallet: Wallet, item: Item, count: Int, credits: Price = 0.0): Price {
        val oldValue = wallet.value()
        val walletWithoutItems = Wallet(wallet.bidderId, wallet.items.toMutableMap(), wallet.credits)
        walletWithoutItems.update(item, -count, credits)
        val oldValueWithoutItems = walletWithoutItems.value()
        return (oldValue - oldValueWithoutItems).toDouble()
    }
}