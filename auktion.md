## Auktionen

Gegeben $n$ Bieter mit Wertschätzungen $v_i$ für ein Gut
Die Bieter bieten $b_i$ für das Gut in einer Auktion

#### Englische Auktion

Vorgehen:
- Der Auktionator gibt einen Startpreis $p_0$ vor
- Die Bieter bieten nacheinander höhere Preise
- Der Bieter mit dem höchsten Gebot erhält den Zuschlag
- Der Bieter zahlt den Preis des zweithöchsten Gebots

Eigenschaften:
- Englische Auktion ist effizient: Strategie wird durch Preislimit bestimmt
- Preis: Wertschätzung des zweithöchsten Bieters
- Anfällig gegenüber Kollusionen: eine erfolgreiche Absprache führt zu einem geringen Erlös
- Abbruch von Absprache führt trotzdem dazu, dass der Meistwertschätzende darauf reagiert

Typische FIPA-Nachrichten (aus der VL)

```yaml
# start auction: publish startAuction
- from: initiator
	to: participant
	action: inform-start-of-auction
# call for proposal: tell Registered
- from: initiator
	to: participant
	action: cfp-1
# did not understand what auction
- from: participant
	to: initiator
	action: not-understood
# this is my proposal: publish LookingFor / ask Offer
- from: participant
	to: initiator
	action: propose
# this proposal is not enough: false
- from: initiator
	to: participant
	action: reject-proposal
# good proposal: true
- from: initiator
	to: participant
	action: accept-proposal
# propose higher: publish LookingFor / ask Offer
- from: initiator
	to: partcipant
	action: cfp-2
# your proposal passed all steps: tell OfferResult
- from: initiator
	to: participant
	action: inform-2
# here is the good: CashInResult / tell AuctionResult
- from: initiator
	to: participant
	action: request
```

#### Holländische Auktion

Vorgehen:
- Der Auktionator gibt einen Startpreis $p_0$ vor
- Der Preis wird in regelmäßigen Abständen gesenkt
- Der erste Bieter, der den Preis akzeptiert, erhält den Zuschlag
- Der Bieter zahlt den Preis $p_0$

Eigenschaften:
- Der erste Bieter, der die Uhr stoppt, zahlt den angegebenen Preis
- Weniger Kommunikation
- Zahlungsbereitschaft und Risikobereitschaft der Mitbieter ist entscheidend
- Keine beste Strategie möglich
- Absprachen wenig sinnvoll: Bruch der Absprache führt direkt zum Ausgang der Auktion

Typische FIPA-Nachrichten (aus der VL)

```yaml
# start the auction: publish startAuction
- from: initiator
	to: participant
	action: inform-start-of-auction
# call for proposal: tell Registered
- from: initiator
	to: participant
	action: cfp-1
# what auction?
- from: participant
	to: initiator
	action: not-understood
# this is my proposal: publish LookingFor / ask Offer
- from: participant
	to: initiator
	action: propose
# I am against that proposal: false
- from: initiator
	to: participant
	action: reject-proposal
# I am in favor of that proposal: true
- from: initiator
	to: participant
	action: accept-proposal
# Propose again: publish LookingFor / ask Offer
- from: initiator
	to: participant
	action: cfp-2
# No offer above reservation price: None
- from: initiator
	to: participant
	state: no-bids
	action: inform-2
```

#### Erstpreisauktion

Eigenschaften:
- Versiegelte Gebote, Gewinner bezahlt den höchsten Preis
- Strategisch äquivalent zur holländischen Auktion
- Andere erwarten wahrheitsgemäße Gebote von dem Agent und spielen deshalb strategisch ⇒ Winner's Curse
- Rationale Strategie: $1 - \frac{1}{n}$ fache der eigenen Wertschätzung zu bieten
- Stabil gegen mögliche Bieterkollusionen

#### Zweitpreisauktion

- Versiegelte Gebote, Gewinner bezahlt den zweithöchsten Preis
- Strategisch äquivalent zur englischen Auktion
- Alle Agenten verhalten sich strategisch wahrheitsgemäß $b_i = v_i$
    - $b_1 = v_1$ ist effizient und ergibt durch den Zuschlag bei $v_2$ einen positiven Nutzen von $v_1 - v_2$
    - Bei $b_1 < v_1$  und $b_1 > v_1$ kann kein höherer Nutzen erzielt werden, weil der Zuschlagpreis ja unabhängig vom Gebot ist
    - Bei $b_1 < v_1$  besteht jedoch eine positive Wahrscheinlichkeit, dass, obwohl $v_1 > v_2$, Spieler $2$ die Auktion gewinnt
    - Bei $b_2 > v_2$ besteht das Risiko für $b_2 > v_1$. Dann würde Bieter $2$ die Auktion gewinnen und einen negativen Nutzen von $v_2 - v_1$ erzielen
- Effizient, kein Fluch des Gewinners
- Trotzdem kann ein lügender Auktionator einen höheren als den tatsächlichen zweiten Preis angeben und den Zuschlagpreis zu seinen Gunsten fälschen
- Anfällig gegenüber Absprachen: Konkurrenten bieten unterhalb ihrer Wertschätzungen und Gewinner wahrheitsgetreu
