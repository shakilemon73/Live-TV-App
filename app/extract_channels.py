import urllib.request
import re

playlists = {
    "BDIX IPTV": "https://github.com/abusaeeidx/Mrgify-BDIX-IPTV/raw/main/playlist.m3u",
    "Animation": "https://github.com/shakilemon73/my-m3u-playlist/raw/refs/heads/main/channel_list/animation.m3u",
    "Auto": "https://github.com/shakilemon73/my-m3u-playlist/raw/refs/heads/main/channel_list/auto.m3u",
    "Bangla Channel": "https://github.com/shakilemon73/my-m3u-playlist/raw/refs/heads/main/channel_list/bangla-channel.m3u",
    "Bangla News": "https://github.com/shakilemon73/my-m3u-playlist/raw/refs/heads/main/channel_list/bangla_news.m3u",
    "Business": "https://github.com/shakilemon73/my-m3u-playlist/raw/refs/heads/main/channel_list/business.m3u",
    "Comedy": "https://github.com/shakilemon73/my-m3u-playlist/raw/refs/heads/main/channel_list/comedy.m3u",
    "Cricket": "https://github.com/shakilemon73/my-m3u-playlist/raw/refs/heads/main/channel_list/cricket.m3u",
    "Documentary": "https://github.com/shakilemon73/my-m3u-playlist/raw/refs/heads/main/channel_list/documentary.m3u",
    "Entertainment": "https://github.com/shakilemon73/my-m3u-playlist/raw/refs/heads/main/channel_list/entertainment.m3u",
    "International News": "https://github.com/shakilemon73/my-m3u-playlist/raw/refs/heads/main/channel_list/international_news.m3u",
    "Kids": "https://github.com/shakilemon73/my-m3u-playlist/raw/refs/heads/main/channel_list/kids.m3u",
    "Lifestyle": "https://github.com/shakilemon73/my-m3u-playlist/raw/refs/heads/main/channel_list/lifestyle.m3u",
    "Movies": "https://github.com/shakilemon73/my-m3u-playlist/raw/refs/heads/main/channel_list/movies.m3u",
    "Music": "https://github.com/shakilemon73/my-m3u-playlist/raw/refs/heads/main/channel_list/music.m3u",
    "Religious": "https://github.com/shakilemon73/my-m3u-playlist/raw/refs/heads/main/channel_list/religious.m3u",
    "Sports & Football": "https://github.com/shakilemon73/my-m3u-playlist/raw/refs/heads/main/channel_list/sports_football.m3u",
    "Doms9 Base": "https://github.com/doms9/iptv/raw/refs/heads/default/M3U8/base.m3u8",
    "Doms9 US TV": "https://github.com/doms9/iptv/raw/refs/heads/default/M3U8/TV.m3u8"
}

extinf_pattern = re.compile(r'#EXTINF:.*?,(.*)')

for name, url in playlists.items():
    print(f"\n==================================================")
    print(f"PLAYLIST: {name}")
    print(f"==================================================")
    try:
        req = urllib.request.Request(
            url, 
            headers={'User-Agent': 'Mozilla/5.0'}
        )
        with urllib.request.urlopen(req, timeout=10) as response:
            content = response.read().decode('utf-8', errors='ignore')
            lines = content.splitlines()
            channels = []
            for line in lines:
                if line.startswith("#EXTINF"):
                    match = extinf_pattern.search(line)
                    if match:
                        chan_name = match.group(1).strip()
                        if chan_name:
                            channels.append(chan_name)
            
            # De-duplicate and sort
            unique_channels = sorted(list(set(channels)))
            print(f"Total Channels found: {len(channels)} (Unique: {len(unique_channels)})")
            for idx, chan in enumerate(unique_channels, 1):
                print(f"{idx}. {chan}")
    except Exception as e:
        print(f"Error loading {name}: {e}")
