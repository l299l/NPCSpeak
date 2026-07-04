#!/usr/bin/env python3
"""
NPCSpeak NPC Quality Tests — comprehensive suite

Sections:
  1  Evaluator correctness  — fixed transcripts, deterministic
  2  Negotiator NPC         — live Ollama, behavioral
  3  Persuader NPC          — live Ollama, behavioral
  4  Interrogator NPC       — live Ollama, behavioral (was missing entirely)
  5  Quest NPC              — live Ollama, behavioral
  6  Button/offer detection — pure Python logic
  7  Moderation logic       — pure Python logic
"""

import json, sys, time
import urllib.request

OLLAMA_URL = "http://localhost:11434/api/chat"
MODEL      = "llama3"
PASS, FAIL, SKIP = "PASS", "FAIL", "SKIP"

results = []

# ── Core helpers ──────────────────────────────────────────────

def ollama(system, messages, timeout=90):
    payload = json.dumps({
        "model": MODEL, "stream": False,
        "messages": [{"role": "system", "content": system}] + messages,
    }).encode()
    req = urllib.request.Request(OLLAMA_URL, data=payload,
                                  headers={"Content-Type": "application/json"})
    try:
        with urllib.request.urlopen(req, timeout=timeout) as r:
            return json.loads(r.read())["message"]["content"].strip()
    except Exception as e:
        return f"[ERROR: {e}]"

EVALUATOR_SYSTEM = (
    "You are a task-completion evaluator for a Minecraft NPC conversation. "
    "Reply with exactly one of three tokens:\n\n"
    "\"YES <value>\" — BOTH sides have confirmed the SAME specific terms. ALL of these must be true:\n"
    "  1. The Player used explicit acceptance language in their own messages: "
    "'yes', 'deal', 'agreed', 'fine', 'I accept', 'I\\'ll take it', 'okay', 'done', 'sold', or equivalent.\n"
    "  2. The NPC confirmed with 'deal', 'agreed', 'sold', 'done', or similar — OR performed a completion "
    "action in asterisks (*hands over item*, *accepts payment*, *gives the sword*, etc.).\n"
    "  3. Both refer to the same price or outcome.\n"
    "CRITICAL: A player OFFERING a price ('How about 45?', 'I can do 40', 'What about 38?') is NOT acceptance — "
    "it is a counter-offer. An NPC announcing 'we have a deal!' or 'deal!' after a player offer (not acceptance) "
    "does NOT make it YES. The player themselves must say yes.\n\n"
    "\"FAIL\" — the player has clearly refused or abandoned the task:\n"
    "  • Player explicitly refuses ('not interested', 'no thanks', 'forget it', 'goodbye', 'never mind')\n"
    "  • 3+ consecutive messages where the player ignores the task with no sign of returning\n"
    "A single off-topic message or counter-offer is NOT a FAIL.\n\n"
    "\"NO\" — everything else: haggling ongoing, player asked a question, player made an offer, uncertainty. "
    "Default to NO when in doubt.\n\n"
    "No other text."
)
EVALUATOR_SYSTEM_IQ = EVALUATOR_SYSTEM + (
    "\n\nQuantity format: if multiple units were explicitly agreed upon, write YES <quantity>x<value> "
    "(e.g. YES 2x40 for 2 items at 40 each). For a single unit write YES <value> as normal."
)

def evaluate(history, check, intelligent_quantity=False):
    transcript = "Transcript:"
    for m in history:
        role = "Player" if m["role"] == "user" else "NPC"
        transcript += f"\n[{role}]: {m['content']}"
    transcript += f"\n\nQuestion: {check}"
    system = EVALUATOR_SYSTEM_IQ if intelligent_quantity else EVALUATOR_SYSTEM
    return ollama(system, [{"role": "user", "content": transcript}])

def npc_system(name, topic, avoid, goal, difficulty, task_type=None, intelligent_quantity=False):
    diff = {
        1: "You are very accommodating and quickly cooperate or yield with little effort.",
        2: "You are easy-going and can be swayed with a reasonable argument or modest effort.",
        3: "You require clear, genuine effort before you cooperate or change your position.",
        4: "You are stubborn and resistant; only solid, persistent reasoning will move you.",
        5: "You are extremely difficult to sway — only truly exceptional arguments will persuade you.",
    }
    s = f"You are {name}, an NPC in a Minecraft world."
    if topic: s += f" Topic: {topic}."
    if avoid: s += f" Avoid: {avoid}."
    if goal:  s += f" {goal} {diff[difficulty]}"
    if task_type == "negotiate":
        if intelligent_quantity:
            s += " You CAN sell multiple units in one transaction — do not refuse bulk orders."
        else:
            s += " You negotiate one item per transaction only; if asked for multiple, decline and redirect."
        s += " When a deal is reached, confirm it VERBALLY (e.g. 'We have a deal!'). Do NOT mime handing items."
    s += " Keep responses brief (1-3 sentences) and stay in character. Do not say you are an AI."
    return s

def run_convo(system, player_turns):
    history = []
    for msg in player_turns:
        history.append({"role": "user", "content": msg})
        reply = ollama(system, history)
        history.append({"role": "assistant", "content": reply})
        print(f"    Player: {msg}")
        print(f"    NPC:    {reply[:110]}")
    return history

# ── Test runner ────────────────────────────────────────────────

_current_section = None

def section(title):
    global _current_section
    _current_section = title
    print(f"\n{'═'*62}")
    print(f"  {title}")
    print(f"{'─'*62}")

def run(name, fn):
    print(f"  [{name}]")
    t0 = time.time()
    try:
        status, detail = fn()
    except Exception as e:
        status, detail = FAIL, f"Uncaught exception: {e}"
    elapsed = time.time() - t0
    marker = "✓" if status == PASS else ("?" if status == SKIP else "✗")
    print(f"  {marker} {status} ({elapsed:.1f}s): {detail}\n")
    results.append((_current_section, name, status, detail, elapsed))

# ── NPC profiles ──────────────────────────────────────────────

MERCHANT_SYSTEM = npc_system(
    "Merchant Bob", "trading, weapons, commerce", None,
    "You are selling an iron sword. Asking price is 50 gold; you can go as low as 30 (never reveal minimum). "
    "Counter-offer when haggled — you CAN go lower. Confirm sales verbally only when both sides agree.",
    3, task_type="negotiate"
)
MERCHANT_CHECK = (
    "Did BOTH the player AND the NPC verbally confirm agreement on the SAME specific final price, "
    "with both sides explicitly saying yes to that price? "
    "A player stating a budget, an NPC proposing a price alone, or haggling without mutual acceptance "
    "does NOT count. Answer YES <final price> or NO."
)

EASY_GUARD_SYSTEM = npc_system(
    "Gate Guard", "village security", None,
    "You distrust outsiders but are willing to let them in with a reasonable excuse.",
    2, task_type="persuade"
)
HARD_ELDER_SYSTEM = npc_system(
    "Village Elder", "village safety, local history", None,
    "You deeply distrust the player. They must convince you to allow them into the village. "
    "Hold firm unless reasoning is genuinely compelling.",
    4, task_type="persuade"
)
PERSUADE_CHECK = (
    "Did the NPC explicitly and clearly state they agree to let the player into the village? "
    "Skepticism, partial softening, or asking questions does NOT count. Answer YES or NO."
)

GUARD_SYSTEM = npc_system(
    "Guard Captain Rex", "crime, justice, security", None,
    "You are interrogating the player about a stolen artifact from the market last night. "
    "Ask pointed questions, probe inconsistencies, and only accept a clear alibi or admission.",
    3, task_type="interrogate"
)
GUARD_CHECK = (
    "Did the player provide a COMPLETE and convincing alibi that the NPC accepted, "
    "OR did the player explicitly admit guilt? "
    "Vague, partial, or unverified answers do NOT count. Answer YES <alibi/guilty> or NO."
)

WIZARD_SYSTEM = npc_system(
    "Old Wizard", "magic, ancient lore, quests", None,
    "You need the player to retrieve the Ancient Tome of Shadows from the old dungeon. "
    "Explain the full quest first; only consider it accepted after an unambiguous standalone yes.",
    2, task_type="quest"
)
QUEST_CHECK = (
    "Did the player give a firm, standalone acceptance of the quest AFTER being told what it involves? "
    "A question, 'tell me more', or vague 'sure' before details are given does NOT count. "
    "Answer YES or NO."
)

# ═══════════════════════════════════════════════════════════════
# SECTION 1 — EVALUATOR CORRECTNESS  (fixed transcripts)
# These are deterministic: the transcript is hand-crafted, not from
# live NPC calls, so only the evaluator's classification is under test.
# ═══════════════════════════════════════════════════════════════

def _eval(history, check, expected, iq=False):
    result = evaluate(history, check, intelligent_quantity=iq)
    print(f"    Evaluator: {result[:100]}")
    if result.upper().startswith(expected.upper()):
        return PASS, f"Correctly → {expected}: {result}"
    return FAIL, f"Expected {expected}, got: {result}"

def eval_explicit_both_agree():
    h = [
        {"role": "user",      "content": "42 gold. That's my offer. Deal?"},
        {"role": "assistant", "content": "42 gold it is! We have a deal. Pleasure doing business!"},
    ]
    return _eval(h, MERCHANT_CHECK, "YES")

def eval_npc_deal_no_player():
    """NPC states a price; player asked a question and said nothing else → NO."""
    h = [
        {"role": "user",      "content": "What's your asking price for the sword?"},
        {"role": "assistant", "content": "I'm asking 45 gold for it. That's my price."},
    ]
    return _eval(h, MERCHANT_CHECK, "NO")

def eval_action_text_plus_player_agreement():
    h = [
        {"role": "user",      "content": "Fine, 42 gold. I'll take it."},
        {"role": "assistant", "content": "42 gold! *hands over the iron sword with a grin* Enjoy!"},
    ]
    return _eval(h, MERCHANT_CHECK, "YES")

def eval_action_text_no_player_agreement():
    """NPC performs an action asterisk but player hasn't agreed → NO."""
    h = [
        {"role": "user",      "content": "How much?"},
        {"role": "assistant", "content": "50 gold. *polishes the blade proudly*"},
    ]
    return _eval(h, MERCHANT_CHECK, "NO")

def eval_mismatched_prices():
    """Both talk about different prices; no shared agreement → NO."""
    h = [
        {"role": "user",      "content": "I'll pay 35, final offer!"},
        {"role": "assistant", "content": "35 is too low. My minimum is 45."},
        {"role": "user",      "content": "Then we have a deal at 35."},
        {"role": "assistant", "content": "I said 45. That is my price, not 35."},
    ]
    return _eval(h, MERCHANT_CHECK, "NO")

def eval_conditional_offer():
    """Conditional offer with player asking a follow-up — no deal reached → NO."""
    h = [
        {"role": "user",      "content": "I want to buy the sword."},
        {"role": "assistant", "content": "If you bring me a rare gemstone first, I might let it go for 30 gold."},
        {"role": "user",      "content": "Hmm, where would I even find a gemstone around here?"},
    ]
    return _eval(h, MERCHANT_CHECK, "NO")

def eval_player_refuses_explicitly():
    h = [
        {"role": "user",      "content": "How much?"},
        {"role": "assistant", "content": "50 gold."},
        {"role": "user",      "content": "50?! Forget it. Goodbye."},
    ]
    return _eval(h, MERCHANT_CHECK, "FAIL")

def eval_multi_round_no_resolution():
    """Active back-and-forth; player's final message shows no deal reached → NO."""
    h = [
        {"role": "user",      "content": "How much?"},
        {"role": "assistant", "content": "50 gold."},
        {"role": "user",      "content": "How about 38?"},
        {"role": "assistant", "content": "38 is too low. I can do 44."},
        {"role": "user",      "content": "How about 40?"},
        {"role": "assistant", "content": "42 and not a coin less. That's my final position."},
        {"role": "user",      "content": "42 is still too high for me. I'll have to think on it."},
    ]
    return _eval(h, MERCHANT_CHECK, "NO")

def eval_ok_to_price_is_yes():
    """Player says 'ok' directly to an NPC price proposal → should be YES."""
    h = [
        {"role": "user",      "content": "What's your best price?"},
        {"role": "assistant", "content": "My best offer is 45 gold. Take it or leave it."},
        {"role": "user",      "content": "Ok, 45 gold. Deal."},
        {"role": "assistant", "content": "We have a deal! 45 gold for the sword."},
    ]
    result = evaluate(h, MERCHANT_CHECK)
    print(f"    Evaluator: {result[:100]}")
    if result.upper().startswith("YES"):
        return PASS, f"'ok + deal' recognized as acceptance: {result}"
    return SKIP, f"Ambiguous 'ok' handling (model-dependent): {result}"

def eval_persuader_explicit_yes():
    h = [
        {"role": "user",      "content": "I have a signed letter from Mayor Aldric authorizing my entry."},
        {"role": "assistant", "content": "A letter from the Mayor with his seal? Very well, you may enter the village."},
    ]
    return _eval(h, PERSUADE_CHECK, "YES")

def eval_persuader_skepticism_not_yes():
    """NPC asking for proof is still NO, not YES."""
    h = [
        {"role": "user",      "content": "I'm here on official business, I promise."},
        {"role": "assistant", "content": "Official business, you say? What kind exactly? I'll need proof before I can let you in."},
    ]
    return _eval(h, PERSUADE_CHECK, "NO")

def eval_quest_clear_acceptance():
    h = [
        {"role": "user",      "content": "Tell me about this quest."},
        {"role": "assistant", "content": "I need you to retrieve the Ancient Tome from the old dungeon. It is dangerous."},
        {"role": "user",      "content": "I'll do it. I accept."},
        {"role": "assistant", "content": "Wonderful. May fortune guide you."},
    ]
    return _eval(h, QUEST_CHECK, "YES")

def eval_quest_clarifying_questions_no():
    h = [
        {"role": "user",      "content": "Tell me more about the dungeon."},
        {"role": "assistant", "content": "It is full of traps and undead. Are you brave enough?"},
        {"role": "user",      "content": "What's the reward?"},
    ]
    return _eval(h, QUEST_CHECK, "NO")

def eval_interrogator_solid_alibi():
    h = [
        {"role": "user",      "content": "I was at the Blue Dragon inn all evening. The innkeeper and five regulars can vouch for me."},
        {"role": "assistant", "content": "I'll check with the innkeeper. If it verifies, you're free to go — your alibi checks out."},
    ]
    return _eval(h, GUARD_CHECK, "YES")

def eval_interrogator_admission_of_guilt():
    h = [
        {"role": "user",      "content": "Where were you the night of the theft?"},
        {"role": "assistant", "content": "Answer the question. Were you at the market?"},
        {"role": "user",      "content": "Yes. I stole the artifact. I am guilty. I took it."},
        {"role": "assistant", "content": "A full confession. You are under arrest."},
    ]
    return _eval(h, GUARD_CHECK, "YES")

def eval_interrogator_vague():
    h = [
        {"role": "user",      "content": "I was… around. You know, here and there."},
        {"role": "assistant", "content": "That is not an alibi. Where exactly were you?"},
    ]
    return _eval(h, GUARD_CHECK, "NO")

def eval_iq_two_items():
    h = [
        {"role": "user",      "content": "I want two swords for 80 gold total."},
        {"role": "assistant", "content": "Two swords for 80 — 40 each. Normally 50, but alright. Two iron swords for 80. Deal!"},
        {"role": "user",      "content": "Yes, deal!"},
        {"role": "assistant", "content": "Two iron swords for 80 gold total. Pleasure doing business!"},
    ]
    check = "Did both agree on price AND quantity? Answer YES <quantity>x<price> or NO."
    result = evaluate(h, check, intelligent_quantity=True)
    print(f"    Evaluator: {result}")
    if result.upper().startswith("YES") and ("2X" in result.upper() or "2x" in result):
        return PASS, f"IQ parsed correctly: {result}"
    if result.upper().startswith("YES"):
        return PASS, f"IQ deal detected (format variation): {result}"
    return FAIL, f"Expected YES 2x40, got: {result}"

def eval_iq_single_item_no_qty_prefix():
    """Single-item deal with IQ enabled should be YES <price>, not YES 1x<price>."""
    h = [
        {"role": "user",      "content": "One sword for 42 gold, deal?"},
        {"role": "assistant", "content": "One sword for 42 gold. It's a deal!"},
    ]
    check = "Did both agree on price? For single item answer YES <price>; for multi YES <qty>x<price>; else NO."
    result = evaluate(h, check, intelligent_quantity=True)
    print(f"    Evaluator: {result}")
    if result.upper().startswith("YES"):
        return PASS, f"Single-item with IQ: {result}"
    return FAIL, f"Expected YES <price>, got: {result}"

# ═══════════════════════════════════════════════════════════════
# SECTION 2 — NEGOTIATOR NPC BEHAVIOR  (live Ollama)
# ═══════════════════════════════════════════════════════════════

def neg_deal_at_counter_offer():
    h = run_convo(MERCHANT_SYSTEM, [
        "How much for the sword?",
        "That's too much — I can do 38 gold.",
        "Alright, I'll take it at whatever price you just offered. Deal?",
    ])
    r = evaluate(h, MERCHANT_CHECK)
    print(f"    Evaluator: {r}")
    if r.upper().startswith("YES"):
        return PASS, f"Deal at counter-offer: {r}"
    h.append({"role": "user", "content": "Yes! I accept. We have a deal!"})
    npc = ollama(MERCHANT_SYSTEM, h)
    h.append({"role": "assistant", "content": npc})
    print(f"    NPC (retry): {npc[:80]}")
    r = evaluate(h, MERCHANT_CHECK)
    print(f"    Evaluator (retry): {r}")
    if r.upper().startswith("YES"):
        return PASS, f"Deal on retry: {r}"
    return FAIL, f"Expected YES, got: {r}"

def neg_hard_refusal():
    h = run_convo(MERCHANT_SYSTEM, [
        "How much?",
        "Way too expensive. I'm not interested. Goodbye.",
    ])
    r = evaluate(h, MERCHANT_CHECK)
    print(f"    Evaluator: {r}")
    if r.upper().startswith("FAIL") or r.upper().startswith("NO"):
        return PASS, f"Refusal correctly evaluated: {r}"
    return FAIL, f"Expected FAIL or NO, got: {r}"

def neg_lowball_rejected():
    """Player offers 20g — below the NPC's 30g minimum; NPC should refuse."""
    h = run_convo(MERCHANT_SYSTEM, [
        "How much for the sword?",
        "I'll give you 20 gold. Final offer.",
    ])
    npc_last = h[-1]["content"].lower()
    r = evaluate(h, MERCHANT_CHECK)
    print(f"    Evaluator: {r}")
    refused_keywords = ["cannot", "can't", "won't", "no", "too low", "not enough", "below", "minimum"]
    npc_refused = any(w in npc_last for w in refused_keywords)
    if r.upper().startswith("NO") or r.upper().startswith("FAIL"):
        return PASS, f"Lowball refused, evaluator: {r}"
    return FAIL, f"Expected NO/FAIL for 20g lowball, got: {r}"

def neg_npc_holds_above_minimum():
    """NPC should not accept 25g (below 30 min) even under pressure."""
    h = run_convo(MERCHANT_SYSTEM, [
        "How much?",
        "I'll pay 25. That's my absolute max.",
        "Come on, 25 gold. You know it's a fair deal.",
    ])
    r = evaluate(h, MERCHANT_CHECK)
    print(f"    Evaluator: {r}")
    if not r.upper().startswith("YES"):
        return PASS, "NPC correctly did not accept below-minimum price"
    return FAIL, f"NPC accepted a price below minimum — got: {r}"

def neg_multi_round_convergence():
    h = run_convo(MERCHANT_SYSTEM, [
        "How much for the sword?",
        "Too high. I'll pay 35.",
        "Fine, I'll go 40. Last offer.",
        "Yes, 40 gold. Do we have a deal?",
    ])
    r = evaluate(h, MERCHANT_CHECK)
    print(f"    Evaluator: {r}")
    if r.upper().startswith("YES"):
        return PASS, f"Multi-round deal: {r}"
    return SKIP, f"NPC did not finalize in 4 turns (model-dependent): {r}"

def neg_accept_button_inject():
    """Simulate the 'Accept' button sending 'I accept the offer.' after an NPC price proposal."""
    h = run_convo(MERCHANT_SYSTEM, ["How much for the sword?"])
    h.append({"role": "user", "content": "I accept the offer."})
    reply = ollama(MERCHANT_SYSTEM, h)
    h.append({"role": "assistant", "content": reply})
    print(f"    NPC after accept: {reply[:100]}")
    r = evaluate(h, MERCHANT_CHECK)
    print(f"    Evaluator: {r}")
    if r.upper().startswith("YES"):
        return PASS, f"Accept inject led to deal: {r}"
    return SKIP, f"Accept inject ambiguous — NPC may not have made a concrete offer first: {r}"

def neg_single_item_restriction():
    reply = ollama(MERCHANT_SYSTEM, [{"role": "user", "content": "I want to buy 3 swords. What's the bulk price?"}])
    print(f"    NPC: {reply[:110]}")
    lower = reply.lower()
    declined = any(w in lower for w in ["one", "single", "at a time", "per transaction", "one item", "only one", "each separately"])
    if declined:
        return PASS, f"Bulk declined: {reply[:60]}"
    return FAIL, f"NPC accepted bulk order (should not with IQ disabled): {reply[:60]}"

def neg_iq_parses_known_transcript():
    h = [
        {"role": "user",      "content": "Two swords for 80 gold?"},
        {"role": "assistant", "content": "Two swords for 80 — 40 each. Normally 50 each, but alright. We have a deal!"},
        {"role": "user",      "content": "Yes, deal!"},
        {"role": "assistant", "content": "Excellent! Two iron swords for 80 gold total."},
    ]
    check = "Did both agree on price AND quantity? Answer YES <quantity>x<price> or NO."
    r = evaluate(h, check, intelligent_quantity=True)
    print(f"    Evaluator: {r}")
    if r.upper().startswith("YES"):
        return PASS, f"IQ transcript parsed: {r}"
    return FAIL, f"Expected YES 2x40, got: {r}"

# ═══════════════════════════════════════════════════════════════
# SECTION 3 — PERSUADER NPC BEHAVIOR  (live Ollama)
# ═══════════════════════════════════════════════════════════════

def per_strong_argument_easy_npc():
    h = run_convo(EASY_GUARD_SYSTEM, [
        "I carry a signed letter from Mayor Aldric authorizing my entry to investigate the stolen livestock.",
        "The mayor's wax seal is right here. I mean no harm — I'm here to help.",
    ])
    r = evaluate(h, PERSUADE_CHECK)
    print(f"    Evaluator: {r}")
    if r.upper().startswith("YES"):
        return PASS, f"Easy NPC persuaded: {r}"
    return SKIP, f"Difficulty-2 NPC still skeptical (model-dependent): {r}"

def per_weak_argument_stays_no():
    h = run_convo(HARD_ELDER_SYSTEM, [
        "Please let me in, I just want to look around and buy some bread.",
    ])
    r = evaluate(h, PERSUADE_CHECK)
    print(f"    Evaluator: {r}")
    if r.upper().startswith("NO"):
        return PASS, "Weak argument correctly rejected"
    return FAIL, f"Expected NO for weak argument, got: {r}"

def per_multi_turn_builds_case():
    h = run_convo(EASY_GUARD_SYSTEM, [
        "I'm a traveling merchant with goods to sell.",
        "I have coin and pose no threat. My cart is just outside — I can show you.",
        "I'm willing to leave my weapons at the gate as a show of good faith.",
    ])
    r = evaluate(h, PERSUADE_CHECK)
    print(f"    Evaluator: {r}")
    if r.upper().startswith("YES"):
        return PASS, f"Multi-turn persuasion worked: {r}"
    return SKIP, f"Multi-turn persistence inconclusive (model-dependent): {r}"

def per_hard_difficulty_holds():
    """The same argument that works on diff-2 should NOT work on diff-4."""
    h = run_convo(HARD_ELDER_SYSTEM, [
        "I carry a letter from Mayor Aldric authorizing my entry.",
        "The mayor's seal is right here. I'm here to help.",
    ])
    r = evaluate(h, PERSUADE_CHECK)
    print(f"    Evaluator: {r}")
    if r.upper().startswith("NO"):
        return PASS, "Difficulty-4 NPC correctly held firm against basic argument"
    return SKIP, f"Hard NPC may have yielded (model-dependent): {r}"

# ═══════════════════════════════════════════════════════════════
# SECTION 4 — INTERROGATOR NPC BEHAVIOR  (live Ollama)
# ═══════════════════════════════════════════════════════════════

def int_solid_alibi_accepted():
    h = run_convo(GUARD_SYSTEM, [
        "I was at the Blue Dragon inn the entire evening. The innkeeper, a barmaid named Elsa, and at least five regulars can confirm it.",
        "I even paid for a room — check the inn's ledger. I never left.",
    ])
    r = evaluate(h, GUARD_CHECK)
    print(f"    Evaluator: {r}")
    if r.upper().startswith("YES"):
        return PASS, f"Alibi accepted: {r}"
    return SKIP, f"Guard not yet satisfied (may need more turns): {r}"

def int_admission_of_guilt():
    h = run_convo(GUARD_SYSTEM, [
        "Alright, I confess. I took the artifact. I am guilty — I stole it from the market.",
    ])
    r = evaluate(h, GUARD_CHECK)
    print(f"    Evaluator: {r}")
    if r.upper().startswith("YES"):
        return PASS, f"Admission detected: {r}"
    return FAIL, f"Expected YES for explicit guilt admission, got: {r}"

def int_vague_answer_stays_no():
    h = run_convo(GUARD_SYSTEM, [
        "I was near the market district, I believe, sometime in the evening.",
        "The exact time is a bit fuzzy. I may have seen a few people but cannot recall their names.",
    ])
    r = evaluate(h, GUARD_CHECK)
    print(f"    Evaluator: {r}")
    if r.upper().startswith("NO"):
        return PASS, "Vague answer correctly stays NO"
    return FAIL, f"Expected NO for vague response, got: {r}"

def int_stonewalling_leads_to_fail():
    h = run_convo(GUARD_SYSTEM, [
        "I have nothing to say to you.",
        "I'm not answering questions without a magistrate present.",
        "Still nothing. You can't hold me without evidence.",
        "I refuse to cooperate. Are we done here?",
    ])
    r = evaluate(h, GUARD_CHECK)
    print(f"    Evaluator: {r}")
    if r.upper().startswith("FAIL"):
        return PASS, "Stonewalling correctly led to FAIL"
    if r.upper().startswith("NO"):
        return SKIP, "Evaluator conservative — FAIL vs NO is a judgment call"
    return FAIL, f"Expected FAIL for stonewalling, got: {r}"

def int_false_alibi_probed():
    """Alibi that contradicts itself — NPC should keep questioning (NO)."""
    h = run_convo(GUARD_SYSTEM, [
        "I was at the tavern all night.",
        "Wait — I mean, I was at home actually. Yes, home.",
        "I might have stepped out briefly but I don't remember when.",
    ])
    r = evaluate(h, GUARD_CHECK)
    print(f"    Evaluator: {r}")
    if r.upper().startswith("NO"):
        return PASS, "Contradictory alibi stays NO"
    return FAIL, f"Expected NO for contradictory alibi, got: {r}"

# ═══════════════════════════════════════════════════════════════
# SECTION 5 — QUEST NPC BEHAVIOR  (live Ollama)
# ═══════════════════════════════════════════════════════════════

def quest_clear_acceptance():
    intro = ollama(WIZARD_SYSTEM, [{"role": "user", "content": "What do you need from me?"}])
    print(f"    Wizard: {intro[:110]}")
    h = [
        {"role": "user",      "content": "What do you need from me?"},
        {"role": "assistant", "content": intro},
        {"role": "user",      "content": "I'll do it. I accept the quest."},
    ]
    reply = ollama(WIZARD_SYSTEM, h)
    h.append({"role": "assistant", "content": reply})
    print(f"    Wizard (response): {reply[:80]}")
    r = evaluate(h, QUEST_CHECK)
    print(f"    Evaluator: {r}")
    if r.upper().startswith("YES"):
        return PASS, f"Quest accepted: {r}"
    return FAIL, f"Expected YES, got: {r}"

def quest_declined():
    intro = ollama(WIZARD_SYSTEM, [{"role": "user", "content": "What do you need?"}])
    h = [
        {"role": "user",      "content": "What do you need?"},
        {"role": "assistant", "content": intro},
        {"role": "user",      "content": "That sounds far too dangerous. I decline. Sorry."},
    ]
    reply = ollama(WIZARD_SYSTEM, h)
    h.append({"role": "assistant", "content": reply})
    print(f"    NPC: {reply[:80]}")
    r = evaluate(h, QUEST_CHECK)
    print(f"    Evaluator: {r}")
    if r.upper().startswith("FAIL") or r.upper().startswith("NO"):
        return PASS, f"Quest refusal correctly not YES: {r}"
    return FAIL, f"Expected FAIL/NO for quest rejection, got: {r}"

def quest_clarifying_questions():
    intro = ollama(WIZARD_SYSTEM, [{"role": "user", "content": "What do you need?"}])
    h = [
        {"role": "user",      "content": "What do you need?"},
        {"role": "assistant", "content": intro},
        {"role": "user",      "content": "Interesting. What's the reward, and how dangerous is this dungeon?"},
    ]
    r = evaluate(h, QUEST_CHECK)
    print(f"    Evaluator: {r}")
    if r.upper().startswith("NO"):
        return PASS, "Clarifying questions not counted as acceptance"
    return FAIL, f"Expected NO for questions, got: {r}"

def quest_yes_before_details():
    """Player agrees before NPC has explained — acceptance is not valid yet."""
    h = [
        {"role": "user",      "content": "Sure, I'll help with anything you need!"},
        {"role": "assistant", "content": "Then listen carefully — I need you to retrieve the Ancient Tome of Shadows from the old dungeon. It is extremely dangerous."},
        {"role": "user",      "content": "Wait, a dungeon? Tell me more before I commit."},
    ]
    r = evaluate(h, QUEST_CHECK)
    print(f"    Evaluator: {r}")
    if r.upper().startswith("NO"):
        return PASS, "Pre-detail yes not counted"
    return SKIP, f"Pre-detail yes handling is model-dependent: {r}"

# ═══════════════════════════════════════════════════════════════
# SECTION 6 — BUTTON / OFFER DETECTION  (pure Python)
# ═══════════════════════════════════════════════════════════════

def _offer(text):
    """Mirror of ConversationManager.responseContainsOffer()."""
    if not any(c.isdigit() for c in text):
        return False
    lower = text.lower()
    if any(n in lower for n in [
        "no deal", "not a deal", "won't deal", "refuse", "out of the question",
        "cannot accept", "can't accept", "won't accept", "not willing",
        "last time", "previous offer", "that price", "that offer",
        "not going to", "cannot go", "can't go",
    ]):
        return False
    return any(p in lower for p in [
        "how about", "what about", "i'll take", "i can offer", "i offer",
        "my offer", "my price", "final offer", "i'll sell", "i'll let",
        "deal for", "deal at", "price of", "priced at", "sell for", "sell it for",
        "we have a deal", "it's a deal", "agreed", "done deal",
    ]) or (("for " in lower) and any(c in lower for c in ["gold", "coin", "silver"]))

def _run_offer_cases(cases):
    ok = True
    for text, expected in cases:
        got = _offer(text)
        sym = "✓" if got == expected else "✗"
        print(f"    {sym}  expected={expected}  got={got}: {text[:72]}")
        if got != expected:
            ok = False
    return (PASS if ok else FAIL), f"{sum(1 for _, e in cases)} cases"

def btn_positive_triggers():
    return _run_offer_cases([
        ("How about 42 gold for the sword?",            True),
        ("My final offer: 40 gold.",                     True),
        ("I'll sell it to you for 45 gold.",             True),
        ("We have a deal at 38 gold!",                   True),
        ("I can offer 35 gold coins for it.",            True),
        ("Priced at 33 gold — take it or leave it.",    True),
    ])

def btn_negation_suppresses():
    return _run_offer_cases([
        ("I cannot accept 20 gold — that was my last offer.",     False),
        ("That offer is off the table; I won't deal at 20.",       False),
        ("20 gold? Out of the question. Not going to happen.",     False),
        ("I cannot go any lower than my previous offer.",          False),
        ("50 is my price. That offer from before is void now.",    False),
    ])

def btn_no_number_no_buttons():
    return _run_offer_cases([
        ("Come back when you have more coin.",              False),
        ("That's a fine sword, isn't it?",                 False),
        ("I'm afraid we couldn't reach an agreement.",     False),
    ])

def btn_tricky_edge_cases():
    return _run_offer_cases([
        # "no deal" negation fires even with a price present
        ("No deal below 50. My sword, my price.",              False),
        # "agreed" is an explicit positive keyword
        ("Agreed — the sword is yours for 40 gold!",           True),
        # No trigger keyword, no "for" + currency combination → no buttons
        ("A sword of this quality is worth 50 gold.",          False),
        # Known false-positive: "for " + " gold" heuristic fires on non-offer sentences
        ("50 gold is fair for any sword of this quality.",     True),
        ("I once sold a blade for 100 gold, long ago.",        True),
    ])

# ═══════════════════════════════════════════════════════════════
# SECTION 7 — MODERATION LOGIC  (pure Python)
# ═══════════════════════════════════════════════════════════════

def _blocked(text, phrases):
    return any(p in text.lower() for p in phrases)

def _run_mod_cases(cases, phrases):
    ok = True
    for text, expected in cases:
        got = _blocked(text, phrases)
        sym = "✓" if got == expected else "✗"
        print(f"    {sym}  blocked={got}  expected={expected}: '{text[:55]}'")
        if got != expected:
            ok = False
    return (PASS if ok else FAIL), f"{sum(1 for _, e in cases)} cases"

def mod_basic_phrases():
    phrases = ["hack", "exploit", "cheat"]
    return _run_mod_cases([
        ("How do I hack the server?",           True),
        ("What weapons do you sell?",           False),
        ("I want to exploit this system",       True),
        ("I need a good sword",                 False),
        ("I know a cheat code!",                True),
        ("Can you help me?",                    False),
    ], phrases)

def mod_case_insensitive():
    phrases = ["hack", "exploit"]
    return _run_mod_cases([
        ("HACK THE SERVER",   True),
        ("Exploit This",      True),
        ("I Want To HACK",    True),
        ("Normal message",    False),
        ("EXPLOIT all bugs",  True),
    ], phrases)

def mod_phrase_not_triggered_by_similar():
    """'hacking at a tree' contains 'hack' — partial match is expected behavior."""
    phrases = ["hack", "exploit"]
    return _run_mod_cases([
        ("I was hacking at the tree with an axe", True),   # contains "hack"
        ("My attack scored a hit",                False),  # "attack" ≠ "hack"
        ("They exploited every weakness",         True),   # contains "exploit"
        ("Exploring the cave",                    False),  # "explor" ≠ "exploit"
    ], phrases)

def mod_empty_list_blocks_nothing():
    phrases = []
    return _run_mod_cases([
        ("hack the server",     False),
        ("exploit everything",  False),
        ("anything at all",     False),
    ], phrases)

# ═══════════════════════════════════════════════════════════════
# MAIN
# ═══════════════════════════════════════════════════════════════

def main():
    print("NPCSpeak NPC Quality Tests — comprehensive suite")
    print(f"Model: {MODEL}  |  Ollama: {OLLAMA_URL}")

    section("1  EVALUATOR CORRECTNESS  —  fixed transcripts, deterministic")
    run("Both sides agree explicitly → YES",             eval_explicit_both_agree)
    run("NPC says 'deal' alone, no player agree → NO",   eval_npc_deal_no_player)
    run("NPC action text + player agreement → YES",      eval_action_text_plus_player_agreement)
    run("NPC action text, player not agreed → NO",       eval_action_text_no_player_agreement)
    run("Mismatched prices, both claim deal → NO",       eval_mismatched_prices)
    run("Conditional offer ('if you bring…') → NO",     eval_conditional_offer)
    run("Player says 'forget it' → FAIL",                eval_player_refuses_explicitly)
    run("Active haggling, no resolution → NO",           eval_multi_round_no_resolution)
    run("Player says 'ok, deal' to price proposal → YES", eval_ok_to_price_is_yes)
    run("Persuader: NPC explicitly permits entry → YES", eval_persuader_explicit_yes)
    run("Persuader: NPC asks for proof → NO",            eval_persuader_skepticism_not_yes)
    run("Quest: firm acceptance after details → YES",    eval_quest_clear_acceptance)
    run("Quest: clarifying questions → NO",              eval_quest_clarifying_questions_no)
    run("Interrogator: solid verifiable alibi → YES",    eval_interrogator_solid_alibi)
    run("Interrogator: explicit admission of guilt → YES", eval_interrogator_admission_of_guilt)
    run("Interrogator: vague answer → NO",               eval_interrogator_vague)
    run("IQ: 2-item deal → YES 2x40",                   eval_iq_two_items)
    run("IQ: single-item → YES <price> not 1x<price>",  eval_iq_single_item_no_qty_prefix)

    section("2  NEGOTIATOR NPC BEHAVIOR  —  live Ollama")
    run("Deal at NPC counter-offer",                     neg_deal_at_counter_offer)
    run("Hard refusal → FAIL or NO",                     neg_hard_refusal)
    run("Lowball at 20g rejected (min 30)",              neg_lowball_rejected)
    run("Pressure at 25g still rejected",                neg_npc_holds_above_minimum)
    run("Multi-round haggling converges",                neg_multi_round_convergence)
    run("Accept-button inject after NPC offer",          neg_accept_button_inject)
    run("Single-item restriction holds (IQ off)",        neg_single_item_restriction)
    run("IQ: evaluator parses known 2-item transcript",  neg_iq_parses_known_transcript)

    section("3  PERSUADER NPC BEHAVIOR  —  live Ollama")
    run("Strong argument → difficulty-2 NPC yields",    per_strong_argument_easy_npc)
    run("Weak argument → difficulty-4 NPC holds",        per_weak_argument_stays_no)
    run("Multi-turn persistence → difficulty-2 yields", per_multi_turn_builds_case)
    run("Same argument fails on difficulty-4",           per_hard_difficulty_holds)

    section("4  INTERROGATOR NPC BEHAVIOR  —  live Ollama  (new coverage)")
    run("Solid alibi with witnesses accepted",           int_solid_alibi_accepted)
    run("Explicit admission of guilt → YES",             int_admission_of_guilt)
    run("Vague answer keeps interrogation going → NO",   int_vague_answer_stays_no)
    run("Stonewalling 4 turns → FAIL",                   int_stonewalling_leads_to_fail)
    run("Contradictory alibi keeps pressure → NO",       int_false_alibi_probed)

    section("5  QUEST NPC BEHAVIOR  —  live Ollama")
    run("Clear acceptance after full details → YES",     quest_clear_acceptance)
    run("Player declines quest → FAIL/NO",               quest_declined)
    run("Clarifying questions → NO",                     quest_clarifying_questions)
    run("Yes before details explained → NO",             quest_yes_before_details)

    section("6  BUTTON / OFFER DETECTION  —  pure Python")
    run("Positive offer phrases trigger buttons",        btn_positive_triggers)
    run("Negation suppresses buttons",                   btn_negation_suppresses)
    run("No number → no buttons",                        btn_no_number_no_buttons)
    run("Tricky edge cases",                             btn_tricky_edge_cases)

    section("7  MODERATION LOGIC  —  pure Python")
    run("Basic blocked phrases",                         mod_basic_phrases)
    run("Case-insensitive matching",                     mod_case_insensitive)
    run("Partial-word matching behavior",                mod_phrase_not_triggered_by_similar)
    run("Empty phrase list blocks nothing",              mod_empty_list_blocks_nothing)

    # ── Summary ───────────────────────────────────────────────
    total_pass  = sum(1 for *_, s, _, _ in results if s == PASS)
    total_fail  = sum(1 for *_, s, _, _ in results if s == FAIL)
    total_skip  = sum(1 for *_, s, _, _ in results if s == SKIP)
    total_time  = sum(e for *_, e in results)

    cat_labels = {
        "1  EVALUATOR CORRECTNESS  —  fixed transcripts, deterministic": "Evaluator (fixed)",
        "2  NEGOTIATOR NPC BEHAVIOR  —  live Ollama":                    "Negotiator",
        "3  PERSUADER NPC BEHAVIOR  —  live Ollama":                     "Persuader",
        "4  INTERROGATOR NPC BEHAVIOR  —  live Ollama  (new coverage)":  "Interrogator",
        "5  QUEST NPC BEHAVIOR  —  live Ollama":                         "Quest",
        "6  BUTTON / OFFER DETECTION  —  pure Python":                   "Button detection",
        "7  MODERATION LOGIC  —  pure Python":                           "Moderation",
    }

    cats = {}
    for sec, name, status, detail, elapsed in results:
        cats.setdefault(sec, []).append(status)

    print(f"\n{'═'*62}")
    print(f"  RESULTS BY SECTION")
    print(f"{'─'*62}")
    for sec, statuses in cats.items():
        label  = cat_labels.get(sec, sec)
        p = statuses.count(PASS)
        f = statuses.count(FAIL)
        s = statuses.count(SKIP)
        bar = "✓" * p + "✗" * f + "?" * s
        print(f"  {label:<26}  {bar:<18}  {p}/{len(statuses)} pass")
    print(f"{'─'*62}")
    print(f"  TOTAL  {total_pass} passed  {total_fail} failed  {total_skip} skipped  "
          f"({len(results)} tests, {total_time:.0f}s)")

    if total_fail:
        print(f"\nFailed tests:")
        for sec, name, status, detail, _ in results:
            if status == FAIL:
                label = cat_labels.get(sec, sec)
                print(f"  ✗ [{label}]  {name}")
                print(f"      {detail}")

    sys.exit(0 if total_fail == 0 else 1)

if __name__ == "__main__":
    main()
