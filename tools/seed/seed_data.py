"""Demo content for SocialHub: fictional personas and what they "posted".

Every person here is invented. Photos are Unsplash-licensed (via Lorem Picsum) and are referred to
by their Picsum id; avatars are generated (DiceBear, CC0). `days` = how long ago the item should
appear to have been posted (applied by backdate.py after seeding).
"""

PERSONAS = [
    {"username": "aanya_wanders", "displayName": "Aanya Mehra", "theme": "mountains",
     "bio": "Chasing altitude, one trail at a time 🏔️ Bengaluru → everywhere. Travel films on my channel."},
    {"username": "zoe_frames", "displayName": "Zoe Laurent", "theme": "street",
     "bio": "Street photographer. Film when I can, digital when I must 📷 Paris • Istanbul • NYC"},
    {"username": "rohan_roadtrips", "displayName": "Rohan Iyer", "theme": "road",
     "bio": "6 countries, 40,000 km, one very patient car 🚗 Open Road Diaries"},
    {"username": "maya_coastline", "displayName": "Maya Fernandes", "theme": "sea",
     "bio": "Salt water, sunsets and slow mornings 🌊 Goa-born, always near the sea"},
    {"username": "kabir_outdoors", "displayName": "Kabir Singh", "theme": "forest",
     "bio": "Forest bathing enthusiast 🌲 Hikes, waterfalls and long walks with zero signal"},
    {"username": "ishaan_citylights", "displayName": "Ishaan Kapoor", "theme": "city",
     "bio": "Cities after dark 🌃 Architecture nerd from Mumbai"},
    {"username": "nina_wildlens", "displayName": "Nina Thomas", "theme": "wildlife",
     "bio": "Wildlife photographer 🦁 Patience is 90% of the shot"},
    {"username": "sana_goldenhour", "displayName": "Sana Qureshi", "theme": "golden",
     "bio": "Living for the hour before sunset ✨ Portraits • lifestyle • good company"},
    {"username": "dev_builds", "displayName": "Dev Malhotra", "theme": "tech",
     "bio": "Backend engineer ☕ I explain system design in about a minute. Kafka enjoyer."},
]

# Followers/likers who never post (initial-letter avatars). Fictional names.
LURKERS = [
    "arjun_k", "neha_verma", "li_wei", "carlos_m", "fatima_z", "tom_baker", "ananya_r", "vikram_s",
    "emma_j", "rahul_dev", "sofia_g", "kenji_t", "priya_n", "omar_h", "lucas_p", "meera_k",
    "jack_w", "aisha_b", "nikhil_g", "hannah_l", "siddharth_r", "olivia_c", "farhan_a", "ritu_m",
]

# Account for interviewers / visitors. Its password comes from the environment, never from git.
GUEST = {"username": "guest", "displayName": "Guest Explorer",
         "bio": "Hi! I'm the demo account. Look around, like things, leave a comment 👋"}

POSTS = [
    # aanya_wanders
    ("aanya_wanders", [482], "Woke up to this view and forgot I was cold 🥶➡️😍 #mountains #himalayas #travel", 38),
    ("aanya_wanders", [1036, 806, 872], "Snow camp diaries ⛺ Three days, zero network, infinite stars. Swipe for the valley we hiked into → #camping #trekking #snow", 30),
    ("aanya_wanders", [931], "Layers on layers 🌫️ #mountains #minimal", 21),
    ("aanya_wanders", [294], "Above the clouds and finally quiet. @kabir_outdoors you would love this trail #sunrise #trekking", 12),
    ("aanya_wanders", [54], "Summit squad 🙌 4:30 am start, totally worth it #hiking #friends", 5),
    ("aanya_wanders", [166, 507], "Valleys and fjords that make you feel tiny in the best way #landscape #travel", 1.5),
    # zoe_frames
    ("zoe_frames", [91], "The camera that started it all. A 1950s twin-lens, still going strong 📷 #filmphotography #vintage", 36),
    ("zoe_frames", [435], "New-to-me rangefinder. Already in love #streetphotography #cameragear", 27),
    ("zoe_frames", [398, 670], "Narrow streets and red trams: two cities, one week of wandering #streetphotography #travel", 19),
    ("zoe_frames", [821], "Rush hour, slowed down 🚇 #streetphotography #blackandwhite", 11),
    ("zoe_frames", [409, 975], "Old walls, new stories #architecture #texture", 7),
    ("zoe_frames", [520, 122], "NYC → London. Bridges are my weakness 🌉 @ishaan_citylights your turn #cityscape #bridges", 2),
    # rohan_roadtrips
    ("rohan_roadtrips", [563], "The road to that volcano was 80 km of nothing and I loved every minute #roadtrip #iceland", 34),
    ("rohan_roadtrips", [519, 335], "Valley roads > highways. Fight me 😄 #roadtrip #drive", 24),
    ("rohan_roadtrips", [111], "Found this beauty at a fuel stop. The owner talked for 40 minutes, no complaints #classiccars #roadtrip", 16),
    ("rohan_roadtrips", [141, 247], "Golden hills and red rock: the outback did not disappoint 🇦🇺 #australia #roadtrip", 9),
    ("rohan_roadtrips", [132], "Sunset after the rain. Pulled over just for this #goldenhour #roadtrip", 3),
    # maya_coastline
    ("maya_coastline", [317], "Morning walk, empty pier, coffee in hand ☕🌊 #beach #slowliving", 35),
    ("maya_coastline", [323], "That blue though 💙 #sea #cliffs #travel", 26),
    ("maya_coastline", [645, 499], "Palm trees and pier sunsets. Goa never misses 🌴 @sana_goldenhour this light was made for you #goa #sunset", 17),
    ("maya_coastline", [536], "Night swim? Maybe not today 🌊😅 #ocean #nightphotography", 10),
    ("maya_coastline", [637, 596], "Lazy Sunday on the dock with the best people #weekend #friends", 4),
    ("maya_coastline", [397], "Postcard village unlocked 🧡 #italy #travel", 0.8),
    # kabir_outdoors
    ("kabir_outdoors", [957], "Standing under 300-year-old giants 🌲 #forest #nature", 33),
    ("kabir_outdoors", [471], "Pine forest therapy. No notifications for 6 hours #forestbathing #hiking", 25),
    ("kabir_outdoors", [879, 15], "Crossed this bridge, followed the sound of water, found this waterfall 💦 #waterfall #hiking", 15),
    ("kabir_outdoors", [1018], "Green, green, green 💚 Highland hikes hit different #scotland #hiking", 8),
    ("kabir_outdoors", [562, 567], "Some forests feel haunted. This one felt peaceful #woods #moody", 2.5),
    # ishaan_citylights
    ("ishaan_citylights", [418], "Coin-operated binoculars, million-dollar view 🏙️ #nyc #skyline", 37),
    ("ishaan_citylights", [382], "Rush hour in Bangkok. Organised chaos at its finest 🚦 #bangkok #citylife", 28),
    ("ishaan_citylights", [45, 342], "Tuk-tuks, scooters and street food at midnight. Thailand, week one 🛺 #thailand #streetlife", 20),
    ("ishaan_citylights", [629], "Yes, I did the cliché pose. No, I'm not posting it 😂 #pisa #italy", 13),
    ("ishaan_citylights", [232, 358], "Snowy night in the city ❄️ plus some industrial details #nightphotography #architecture", 6),
    ("ishaan_citylights", [415], "Hollywood in the haze #losangeles #travel", 1),
    # nina_wildlens
    ("nina_wildlens", [1074], "Locked eyes with her for a full minute. Did not breathe once 🦁 #wildlife #lioness", 32),
    ("nina_wildlens", [1003], "Tiny fawn, giant ears 🦌 #wildlifephotography #nature", 22),
    ("nina_wildlens", [258], "Birds at dusk, choreographed by nobody #birds #wildlife", 14),
    ("nina_wildlens", [55, 459], "Small wonders: life finding a way 🌱 #macro #nature", 6.5),
    ("nina_wildlens", [810], "Fog rolling through the reserve at 6 am. Worth the alarm #mist #forest", 0.5),
    # sana_goldenhour
    ("sana_goldenhour", [399], "Golden hour, golden people ✨ #goldenhour #portrait", 31),
    ("sana_goldenhour", [778, 375], "Fields, flowers and the softest light 🌼 #portraitphotography #goldenhour", 23),
    ("sana_goldenhour", [639], "Ferris wheel blues 🎡 #summer #colors", 18),
    ("sana_goldenhour", [452], "Last night's concert was unreal 🎶🔥 #livemusic #concert", 12),
    ("sana_goldenhour", [228, 729], "Countryside weekend: hay bales and a horse-drawn baler, like stepping back in time #countryside #slowliving", 5),
    # dev_builds
    ("dev_builds", [341], "Day 1 of posting something other than code 😅 Hi SocialHub! #developer", 39),
    ("dev_builds", [802], "Planning a side project the old-school way: paper first, code later ✏️ #sideproject #engineering", 29),
    ("dev_builds", [146], "Spotted outside a café. My commute-to-work energy 🚲 #random", 18),
    ("dev_builds", [456, 966], "Weekend reset by the lake. The laptop stayed home (mostly) #worklifebalance", 7),
]

CHANNELS = {
    "aanya_wanders": ("aanyawanders", "Aanya Wanders", "Slow travel films from the mountains. New trek every month.", 482),
    "rohan_roadtrips": ("openroaddiaries", "Open Road Diaries", "Road trips across six countries, one very patient car.", 563),
    "maya_coastline": ("mayacoastline", "Maya by the Sea", "Calm coastal montages for your lunch break.", 645),
    "zoe_frames": ("zoeframes", "Zoe Frames", "Street photography walks, gear talk and film rolls.", 520),
    "nina_wildlens": ("ninawildlens", "Wild Lens", "Wildlife photography: the waiting, the misses and the one shot that works.", 1074),
    "dev_builds": ("devbuilds", "Dev Builds", "System design, explained in about a minute.", None),
}

# Long-form videos: photo slideshows (Ken Burns) or text-slide explainers.
VIDEOS = [
    {"owner": "aanya_wanders", "key": "snowcamp", "days": 29, "category": "TRAVEL",
     "title": "Snow Camp in the Himalayas: 3 Days, No Network",
     "description": "We hiked for two days to camp under the snow peaks. Cold nights, clear skies and a valley I'll never forget.\n\nShot on a phone and a lot of patience.",
     "tags": ["himalayas", "trekking", "camping"], "photos": [482, 1036, 806, 872, 931, 294, 54]},
    {"owner": "aanya_wanders", "key": "misty", "days": 14, "category": "TRAVEL",
     "title": "A Misty Morning Walk (Relaxing, No Talking)",
     "description": "Twenty seconds of fog, hills and quiet. Put it on before a busy day.",
     "tags": ["relaxing", "mountains", "fog"], "photos": [931, 810, 228, 966, 166]},
    {"owner": "rohan_roadtrips", "key": "roadtrip", "days": 22, "category": "TRAVEL",
     "title": "Iceland and Australia Road Trip in 20 Seconds",
     "description": "Two continents, one camera mount. Volcano roads, green valleys and the outback.",
     "tags": ["roadtrip", "iceland", "australia"], "photos": [563, 519, 335, 141, 247, 132]},
    {"owner": "maya_coastline", "key": "coast", "days": 16, "category": "LIFESTYLE",
     "title": "Goa Sunsets: A Slow Coastal Montage",
     "description": "Piers, palms and the bluest water I could find. Slow living, slowly filmed.",
     "tags": ["goa", "sunset", "beach"], "photos": [317, 645, 499, 323, 536, 637]},
    {"owner": "zoe_frames", "key": "streetwalk", "days": 18, "category": "LIFESTYLE",
     "title": "A Street Photography Walk Through Four Cities",
     "description": "From a 1950s twin-lens to a rangefinder: what I shot in Istanbul, London and New York.",
     "tags": ["streetphotography", "film", "travel"], "photos": [91, 435, 398, 670, 821, 409, 520, 122]},
    {"owner": "nina_wildlens", "key": "wild", "days": 10, "category": "ENTERTAINMENT",
     "title": "Wildlife Moments: Patience Pays Off",
     "description": "A lioness, a fawn and a sky full of birds. Hours of waiting, condensed.",
     "tags": ["wildlife", "photography", "nature"], "photos": [1074, 1003, 258, 55, 459, 810]},
    {"owner": "dev_builds", "key": "urlshort", "days": 26, "category": "TECH",
     "title": "System Design in 60s: How a URL Shortener Works",
     "description": "Base62 codes, a key-value store, a cache and a redirect. The classic interview question in under a minute.",
     "tags": ["systemdesign", "backend", "interview"],
     "slides": [
         ("How a URL Shortener Works", ["System design in 60 seconds"]),
         ("1. The API", ["POST /shorten  {url}  →  short code", "GET /{code}  →  301 redirect"]),
         ("2. Generating the code", ["Counter → Base62: 7 chars ≈ 3.5 trillion codes", "or hash + collision check"]),
         ("3. Storage", ["Key-value store: code → long URL", "Reads outnumber writes ~100 : 1"]),
         ("4. Scaling reads", ["Cache hot codes in Redis", "301 is cacheable, 302 keeps analytics"]),
         ("Recap", ["Base62 IDs • KV store • cache • redirect"]),
     ]},
    {"owner": "dev_builds", "key": "kafkarabbit", "days": 12, "category": "TECH",
     "title": "Kafka vs RabbitMQ: Which One Should You Use?",
     "description": "A log of facts vs a queue of jobs. When to pick each, and why many systems use both.",
     "tags": ["kafka", "rabbitmq", "systemdesign"],
     "slides": [
         ("Kafka vs RabbitMQ", ["Which one should you use?"]),
         ("Kafka is a log", ["Events are stored and replayable", "Many consumers read independently", "Ordered within a partition"]),
         ("RabbitMQ is a work queue", ["Each job goes to one worker", "Ack, retry with delay, dead-letter queue"]),
         ("Pick Kafka for…", ["Event-driven services", "Analytics and audit trails"]),
         ("Pick RabbitMQ for…", ["Background jobs: emails, video transcoding", "Per-message retries"]),
         ("Why not both?", ["Kafka for facts, RabbitMQ for tasks"]),
     ]},
    {"owner": "dev_builds", "key": "typeurl", "days": 3, "category": "EDUCATION",
     "title": "What Happens When You Type a URL and Press Enter?",
     "description": "DNS, TCP, TLS, HTTP and rendering: the whole journey in under a minute.",
     "tags": ["networking", "web", "interview"],
     "slides": [
         ("What happens when you type a URL?", ["The whole journey in under a minute"]),
         ("1. DNS", ["Browser cache → OS → resolver", "Root → TLD → authoritative server"]),
         ("2. Connect", ["TCP handshake", "TLS: certificate check and session keys"]),
         ("3. Request", ["GET / over HTTP/2", "Load balancer → web server → response"]),
         ("4. Render", ["Parse HTML → fetch CSS and JS", "Layout → paint → interactive"]),
         ("Recap", ["DNS • TCP • TLS • HTTP • render"]),
     ]},
    {"owner": "dev_builds", "key": "outbox", "days": 0.6, "category": "TECH",
     "title": "The Transactional Outbox Pattern, Explained",
     "description": "How to save to your database and publish an event without ever losing one.",
     "tags": ["microservices", "kafka", "patterns"],
     "slides": [
         ("The Transactional Outbox Pattern", ["Never lose an event again"]),
         ("The problem", ["DB write succeeds, Kafka publish fails", "= a lost event (the dual-write problem)"]),
         ("The fix", ["Write the event to an outbox table", "in the same database transaction"]),
         ("The relay", ["Poll unpublished rows (SKIP LOCKED)", "Publish, then mark as sent"]),
         ("The guarantee", ["At-least-once delivery", "so consumers must be idempotent"]),
         ("Recap", ["One transaction • relay • idempotent consumers"]),
     ]},
]

REELS = [
    ("aanya_wanders", "trek", [294, 54, 1036], "Trek highlights in 10 seconds ⛰️ #reels #trekking", 0.3),
    ("kabir_outdoors", "forest", [957, 471, 879, 15], "A walk in the woods, sped up 🌲 #reels #forest", 4),
    ("ishaan_citylights", "nights", [122, 382, 418, 232], "City lights after midnight 🌃 #reels #nightphotography", 9),
    ("maya_coastline", "beach", [645, 317, 499], "Beach day recap 🌴 #reels #goa", 11),
    ("sana_goldenhour", "golden", [399, 778, 375], "Golden hour in motion ✨ #reels #goldenhour", 2),
]

STORIES = [
    ("aanya_wanders", 507, "Back on the trail tomorrow 🏔️"),
    ("maya_coastline", 645, "Today's office ☀️"),
    ("ishaan_citylights", 122, "Night walk 🌃"),
    ("sana_goldenhour", 778, ""),
    ("kabir_outdoors", 15, "Found it!"),
    ("zoe_frames", 821, "New roll developing 📷"),
]

COMMENTS = {
    "mountains": ["This view is unreal 😍", "Adding this to my bucket list right now", "How cold was it up there?",
                  "Those layers of mountains 🏔️🏔️", "Which trail is this?", "I can feel the fresh air from here",
                  "Absolutely stunning shot", "Okay I need to plan a trek this year"],
    "street": ["The composition here 👌", "Film or digital?", "Love the mood in this one", "Those colours!",
               "You always find the best corners of a city", "This deserves to be printed", "Black and white suits this so well"],
    "road": ["Road trip goals 🚗", "How long was this drive?", "That road looks endless in the best way",
             "Take me with you next time 😄", "The light in this one!", "Adding this route to my list"],
    "sea": ["I can almost hear the waves 🌊", "That blue is unreal 💙", "Take me back to Goa 😭", "Perfect Sunday vibes",
            "Where is this?", "Okay this is my new wallpaper", "Sunsets like this should be illegal 🌅"],
    "forest": ["So peaceful 🌲", "Forest bathing is so underrated", "Where is this trail?", "Those trees are massive!",
               "I need a no-signal weekend like this", "The waterfall 😍", "Green therapy 💚"],
    "city": ["Cities at night hit different 🌃", "The energy in this shot!", "Love the perspective", "Which city should I visit next?",
             "This is so cinematic", "Haha the Pisa caption 😂", "Bangkok traffic is a whole experience"],
    "wildlife": ["Those eyes 😳", "How close were you?!", "Incredible patience", "This is National Geographic level",
                 "So cute 🦌", "The detail in this is insane", "Nature is the best artist"],
    "golden": ["The light ✨", "Golden hour never disappoints", "Such a warm photo", "Okay this is art",
               "The colours are perfect", "Teach me your editing secrets 🙏", "That concert looks amazing 🔥"],
    "tech": ["Great explanation, thanks!", "Saving this for interview prep", "Do one on rate limiters next 🙏",
             "Clear and to the point 👏", "This finally made it click for me", "More of these please!"],
}

AUTHOR_REPLIES = ["Thank you! 🙏", "Haha yes, you have to go!", "Means a lot ❤️", "Will share the details soon!",
                  "Thank you so much 😊", "Right?! Still thinking about it"]

VIDEO_COMMENTS = ["Watched this twice already", "The editing is so smooth", "Subscribed!", "More videos like this please",
                  "This made my day", "Great video 👏", "Short and sweet, love it"]
