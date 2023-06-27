## Annahmen 

Geometrische Verteilung:  
* Multiplikation (bzw. Division) statt Addition (bzw. Subtraktion).
* Geometrisches Mittel `sqrt(a * b)` statt Arithmetisches Mittel `(a + b) / 2`

Median statt Durchschnitt zur Marktpreisbestimmung: 
* Die Höhe der extremen Preise dürfen nicht den Markt beeinflussen (robust to outliers).

Superadditivität: 
* `v(n + 1) - v(n) >= v(n) - v(n - 1)`
* Wertsteigerung durch Kauf ist mindestens so groß wie Wertsteigerung durch Verkauf.
* Ein Agent, der die Mehrheit aller `a`'s besitzt, kann ein Monopol bilden und den Preis manipulieren.

Eigeninteresse:
* Der Agent ist eigeninteressiert, er will seinen Gewinn maximieren.

Strategisches Bieten: 
* Der Agent, der wahrheitsgemäß bietet, wird von den anderen Agenten ausgenutzt, deshalb sollte er strategisch bieten.
* Wer die Präferenzen anderer Agenten gut vorhersieht, kann die Preise manipulieren und dadurch hohe Gewinne erzielen.

`rivalBids`: 
* LookingFor-Nachrichten werden von den Agenten benutzt, um die Preise durch Bluffen zu manipulieren. 
* Ein Agent, der wahrheitsgemäß bietet, würde nicht bluffen.
* Deshalb sollte er immer seine eigene Präferenz bieten unabhängig von allen LookingFor-Nachrichten.

`digest`: 
* Digest-Nachrichten sind die Ergebnisse aller Bids und stellt eine korrekte Zusammenfassung der Marktsituation dar.
* Die Agenten können die Digest-Nachrichten benutzen, um herauszufinden, inwieweit die anderen Agenten bluffen.
* Die wahren Präferenzen anderer zur Kenntnis nehmend, können sie ihre Preisstrategie neu anpassen.

## Preisbestimmung

`getPriceOnRegistered`: 
* Informationsquellen: `wallet`.
* Gesucht: Ein LookingFor-Preis in der ersten Runde.
* "Verhalte dich so, als ob du nicht an `a` interessiert bist, damit du `a` zu einem günstigeren Preis kaufst", oder
* "Verhalte dich so, als ob du an `a` interessiert bist, damit du `a` zu einem höheren Preis verkaufst".
* Preis: Wertschätzung vom Verkaufspreis eines `a`'s

`getPriceOnDigest`:
* Informationsquellen: `wallet`, `digest`, `rivalBids`.
* Gesucht: Ein LookingFor-Preis für die nächste Runde.
* "Verhalte dich so, als ob du nicht an `a` interessiert bist, damit du `a` zu einem günstigeren Preis kaufst", oder
* "Verhalte dich so, als ob du an `a` interessiert bist, damit du `a` zu einem höheren Preis verkaufst".
* `myFakedPrice` gibt den Bluff-Preis des Agenten an. 
* `theirPublicPrice` (Median von `rivalBids`) ist der Indikator für die Bluff-Preise von anderen Agenten.
* `marketPrice` (Median von `digest`) ist der Indikator für die wahren Angebote von anderen Agenten.
* Die Intensität des Bluffs berechnet sich als `marketPrice / theirPublicPrice`: Vergesse nicht, dass die Preisverhältnisse
  geometrisch und nicht arithmetisch sind.
* `theirActualPrice` wird als `marketPrice * marketPrice / theirPublicPrice` berechnet, und gibt die umgekehrten Bluff-Preise an.
* Der Agent will einerseits `theirActualPrice` ausnutzen, andererseits `myFakedPrice` bieten
* Rationales Verhalten, wenn der Preis innerhalb dieses Intervals liegt → Geometrisches Mittel von beiden Preisen.
* Also: `sqrt(myFakedPrice * theirActualPrice)`
* Es ist äquivalent zu `fakeFactor * marketPrice`, wobei `fakeFactor = sqrt(myFakedPrice / theirPublicPrice)`.

`getPriceOnLookingFor`:
* Informationsquellen: `wallet`, `rivalBids`.
* Gesucht: Ein Offer-Preis.
* Hier hat man keine Informationsquelle über die wahren Präferenzen der anderen Agenten (kein `digest`).
* Abhängig davon, wie weit der Bluff-Preis vom Marktpreis entfernt ist, wird seine Entscheidung negativ beeinflusst.
* Deswegen wird `marketPrice` als der Median von `rivalBids` gewählt, um den Preis zu bestimmen.
* Gegeben `marketPrice`, wird jeweils der eigene Gewinn vom Kauf (`buyValue`) und Verkauf (`sellValue`) berechnet.
* Der gewünschte Offer-Preis sollte vergleichbar mit Marktpreis um ein Verschiebungsfaktor `shiftFactor` sein.
* Das gewünschte Verhalten von `getPriceOnLookingFor`:
  * Kaufen ist deutlich lukrativer als Verkaufen: Biete höher als `marketPrice`.
  * Verkaufen ist deutlich lukrativer als Kaufen, mit / ohne Short-Selling: Biete niedriger als `marketPrice`.
  * Weder Kaufen noch Verkaufen macht einen großen Unterschied: Biete nicht.
* `shiftFactor`, definiert als `sqrt(buyValue / sellValue)` (analog zur `(buyValue - sellValue) / 2`), erfüllt alle Bedingungen.
* Falls Short-Selling: `score`-Funktion halbiert `sellValue` als Strafe, so wird `shiftFactor` multiplikativ um `sqrt(2)` erhöht.
* Wenn es dem Agenten egal ist zu kaufen oder zu verkaufen, ist `shiftFactor` insignifikant.
* Die Signifikanz wird durch ein Hyperparameter `offerThreshold` (e.g. `0.9`) bestimmt: 
  * Untere Schranke ist `offerThreshold`: unterhalb der Schranke ist Verkaufen deutlich lukrativer als Kaufen 
  * Obere Schranke ist `1 / offerThreshold`: oberhalb der Schranke ist Kaufe deutlich lukrativer als Verkaufen
  * Wenn `shiftFactor` innerhalb des Intervals liegt, lohnt sich nicht, einen Offer zu schicken.
* Im Endeffekt wird `shiftFactor * marketPrice` geschickt, nur wenn der Offer-Preis signifikant ist.

## Implementierung

```kotlin
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
```