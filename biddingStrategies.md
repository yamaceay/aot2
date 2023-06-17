## Game Theory Perspectives

Let $X$ be a list of all goods in the current auction

Let ${A_1, …, A_n}$ be a set of agents who are bidding (with sorted preferences in descending order: $v_1 > v_2 > …$)

#### Why do Agents Bid The Way They Do?
We know that if $A_1$ wants to buy $X$, it should bid higher than the median price of $X$

- If $A_1$ bids too high, it will increase the median price of $X$, and let agent $A_1$ pay more than it should
- If $A_1$ bids too low, it will be outbid by the agent in the middle, and it will lose the opportunity to buy $X$

#### Explained By Prisoner's Dilemma: Why Do Agents Lie?
We know that if $A_1$ follows a incentive compatible strategy (anreizkompatibel), that means:

* It plays solely based on its own preferences
* It assumes the same for others: Otherwise, it would be a bad strategy
* So, any offer that it publishes shouldn't affect the other agent's preferences
* Therefore: Only bluffing agents are willing to disclose their offers

If all agents would have played honestly, it would be efficient for all of them

But individually: Some agents might want to bluff to get a better deal to gain more utility


The optimal balance is established by the agents who want to maximize their credits by selling the good on a higher price, and by the agents who want to minimize their credits by buying the good on a lower price

Let's consider optimal strategies in both cases assuming every agent plays strategically:

The optimal buying strategy: Bluff to pay $\frac{v_i}{n}$ less than private preference $v_i$
* This is also the next highest bid among all bids lower than this agent's bid, e.g. $v_3$ for $A_2$, $v_4$ for $A_3$. 

Similarly, the optimal selling strategy: Bluff to sell $\frac{v_i}{n}$ more than private preference $v_i$

Then: 
* Percentually more agents who are willing to buy -> higher prices
* Percentually more agents who are willing to sell -> lower prices

If the number of agents who want to buy and sell is balanced, the price will be rougly the same as before 

-> No individual gains from bluffing

This is the same situation as Prisoner's Dilemma. 

Everyone would be better off in terms of social welfare if everyone played honestly, but everyone wants to take advantage of others by playing strategically and making individual profits, so plays everyone strategically and is stuck in a Nash equilibrium.