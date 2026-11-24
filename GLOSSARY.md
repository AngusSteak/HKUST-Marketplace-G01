# HKUST Marketplace

The campus marketplace context: buyers, sellers and admins trading second-hand goods within HKUST. Terms below are the canonical language for the project; use them in code, docs and discussion.

## Language

**User**:
The abstract base concept for any registered participant of the marketplace. Carries what is common to every account: username, full name, credentials, active flag and registration date; logging in, managing one's own profile and viewing one's notification board.
_Avoid_: Account, member, person, credentials holder

**Buyer**:
A student or staff member who browses listings and makes offers, borrow requests and claims; chats with sellers, manages deals, rates sellers and reports listings.
_Avoid_: Customer, client, shopper

**Seller**:
A student or staff member who posts Sale, Lend and Giveaway listings, manages them, responds to offers and requests, views statistics and replies to reviews.
_Avoid_: Vendor, merchant, poster

**Admin**:
The seeded administrator who approves or rejects listings, manages users and all listings, handles reports, oversees deals and moderates ratings. There is no admin registration.
_Avoid_: Moderator, superuser, root

**Role**:
The account type of a user — Buyer, Seller or Admin. A user holds exactly one role, fixed when the account is created; it determines which portal (and which actions) the user may access. Username is unique across all roles.
_Avoid_: Account type, user type, permission group

## Listings

**Listing**:
An item a Seller offers on the marketplace under exactly one Listing Type. Carries the item's title, description, Category, Condition, meet-up Location, photos, submission date, Listing Status and view count; it belongs to exactly one Seller, and the Admin reviews it before it can go live.
_Avoid_: Post, item, product, ad, entry

**Listing Type**:
The three-way classification of a Listing — Sale, Lend or Giveaway — fixed when it is posted, deciding which action a Buyer may take on it.
_Avoid_: Category, kind, mode

**Sale Listing**:
A Listing offered for sale at a price. A Buyer acts on it with a price offer.
_Avoid_: Sell listing, for-sale post

**Lend Listing**:
A Listing offered for borrowing over a lending duration. A Buyer acts on it with a borrow request.
_Avoid_: Loan listing, rental, borrow listing

**Lending duration**:
The length of time a Buyer may keep the item in a Lend Listing; at least five seconds.
_Avoid_: Loan period, rental term

**Giveaway Listing**:
A Listing offered for free. A Buyer acts on it with a claim, and the first successful claim wins.
_Avoid_: Free listing, donation

**Listing Status**:
The lifecycle stage of a Listing: Pending Review, Live, Rejected, Reserved, Sold, Lent, Given or Taken Down.
_Avoid_: State, availability

**Rejection**:
The Admin's refusal of a Listing at review, with a written reason; the Listing never becomes Live. Distinct from a Takedown.
_Avoid_: Decline, refusal

**Takedown**:
The Admin's removal of a Listing from the marketplace while it is Live or Pending Review, with a written reason. Distinct from a Rejection, which happens at review.
_Avoid_: Removal, delisting, delete

**Category**:
The predefined kind of item a Listing offers, chosen by the Seller from a fixed list.
_Avoid_: Type (that means Listing Type), tag

**Condition**:
The predefined wear state of the item in a Listing, chosen by the Seller from a fixed list.
_Avoid_: Quality, state

**Location**:
A predefined HKUST campus location. Used for a Listing's meet-up point, a Seller's preferred meet-up locations, and a Buyer's residence hall.
_Avoid_: Place, venue, address

## Deals

**DealRequest**:
A Buyer's request on a Listing. It takes one of three forms — a Price Offer on a Sale Listing, a Borrow Request on a Lend Listing, or a Claim on a Giveaway Listing — and moves through review by the Seller.
_Avoid_: Request, application, deal application

**Price Offer**:
A DealRequest on a Sale Listing that proposes a price the Seller may accept, decline or counter.
_Avoid_: Bid, offer price

**Borrow Request**:
A DealRequest on a Lend Listing that proposes when the Buyer will take the item and return it.
_Avoid_: Loan application, rental request

**Claim**:
A DealRequest on a Giveaway Listing; the first successful claim wins the item.
_Avoid_: Free request, grab

**Counter**:
the Seller's proposed alternative price (Counter Price) on a Price Offer, or lending period (Counter Period) on a Borrow Request; recorded on the DealRequest, moving it to COUNTERED while it awaits the Buyer's acceptance. The Buyer never counters.
_Avoid_: Counter Offer, counteroffer

## Chat

**ChatThread**:
The private conversation about one Listing between that Listing's Buyer and Seller. There is one ChatThread per (Buyer, Listing) pair, and it remains available after the Listing stops being live.
_Avoid_: Conversation, chat room, discussion

**Message**:
One entry in a ChatThread, carrying its sender, the time it was sent, and either free text or a Formal Offer.
_Avoid_: Chat line, note, post

**Formal Offer**:
A Message that records a DealRequest made from within a ChatThread instead of free text. The request itself is managed through the Deal lifecycle; the Formal Offer is the conversation's record of it.
_Avoid_: Offer message, offer bubble

## Reviews

**Review**:
a Buyer's rating from 1 to 5 with text on a completed Deal, one per Deal.

**Seller Reply**:
the Seller's single response to a Review, notified to the Buyer.

**Flag**:
the Seller's request for Admin attention on an inappropriate Review, distinct from Admin Hide (which removes a Review from public view) and Buyer Report Listing (which targets a Listing).
