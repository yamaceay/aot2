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
* Deswegen wird der Median von `rivalBids` benutzt, um den Preis zu bestimmen.
* Anders als bei `getPriceOnDigest` wird hier nicht geblufft, sondern wahrheitsgemäß geboten.
* Abhängig davon, wie weit der Bluff-Preis vom Marktpreis entfernt ist, kann der Agent den erwünschten Preis nicht bestimmen.

## Implementierung

```kotlin
private fun getPriceOnRegistered(item: Item): Price {
    val fake = scores(item).minBy { it.value } !!
    return fake.value
}

private fun getPriceOnLookingFor(item: Item): Price {
    val marketPrice = median(rivalBids[item]!!)
    val real = scores(item, marketPrice).maxBy { it.value } !!
    return real.value
}

private fun getPriceOnDigest(item: Item): Price {
    val fake = scores(item).minBy { it.value }!!
    val myFakedPrice = fake.value

    val theirPublicPrice = median(rivalBids[item]!!)
    val marketPrice = digest!!.itemStats[item]!!.median
    val fakeFactor = sqrt(myFakedPrice / theirPublicPrice)
    return fakeFactor * marketPrice
}
```

### Fred

Es lohnt sich nicht, gleichzeitig mehrere Produkte zu kaufen. Deshalb präferiert er wenig Produkte, die er initial bekommt hat.
Wenn kein Profit mehr zu machen ist oder so zu scheint, dann verkauft er alles durch CashIn-Requests und reinvestiert seine
Krediten in anderen Produkten. Die teuren Produkte werden shortgesellt ohne Verluste.