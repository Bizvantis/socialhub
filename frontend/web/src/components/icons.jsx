// A small, consistent set of line icons (24px grid, 1.75 stroke), drawn for this app.
// Usage: <Icon name="heart" />, <Icon name="heart" filled className="text-heart" />

const PATHS = {
  home: <><path d="M3 10.5 12 3l9 7.5" /><path d="M5.5 9v11.5h13V9" /><path d="M10 20.5v-6h4v6" /></>,
  compass: <><circle cx="12" cy="12" r="9" /><path d="m15.5 8.5-2 5-5 2 2-5z" /></>,
  video: <><rect x="2.5" y="5" width="19" height="14" rx="3" /><path d="m10.5 9.5 4 2.5-4 2.5z" /></>,
  subscriptions: <><path d="M6 3.5h12M4 6.5h16" /><rect x="2.5" y="9.5" width="19" height="11" rx="2.5" /><path d="m10.5 12.5 3.5 2.5-3.5 2.5z" /></>,
  library: <><path d="M4 4v16M8 4v16" /><path d="m12 4.5 4.5 15.2 3.5-1-4.5-15.2z" /></>,
  bell: <><path d="M6 9a6 6 0 0 1 12 0c0 6 2.5 8 2.5 8h-17S6 15 6 9" /><path d="M10 20a2.2 2.2 0 0 0 4 0" /></>,
  plus: <><path d="M12 5v14M5 12h14" /></>,
  create: <><rect x="3" y="3" width="18" height="18" rx="5" /><path d="M12 8v8M8 12h8" /></>,
  shield: <><path d="M12 21s7.5-3.5 7.5-9.5V5.5L12 3 4.5 5.5v6C4.5 17.5 12 21 12 21z" /></>,
  user: <><circle cx="12" cy="8" r="4" /><path d="M4.5 20.5a7.5 7.5 0 0 1 15 0" /></>,
  search: <><circle cx="11" cy="11" r="7" /><path d="m20.5 20.5-4.6-4.6" /></>,
  heart: <path d="M12 20s-7.5-4.6-7.5-10.2A4.3 4.3 0 0 1 12 7a4.3 4.3 0 0 1 7.5 2.8C19.5 15.4 12 20 12 20z" />,
  comment: <path d="M20.5 11.5a8 8 0 0 1-11.9 7l-4.1 1 1.1-3.9a8 8 0 1 1 14.9-4.1z" />,
  share: <><path d="M21 3 10.5 13.5" /><path d="m21 3-6.5 18-4-7.5L3 9.5z" /></>,
  bookmark: <path d="M18.5 21 12 16.5 5.5 21V5a2 2 0 0 1 2-2h9a2 2 0 0 1 2 2z" />,
  thumbDown: <><path d="M17 3.5v10" /><path d="M17 13.5 13 21a2.5 2.5 0 0 1-2.5-2.5V15H5.2a2 2 0 0 1-2-2.3l1.2-7.5A2 2 0 0 1 6.4 3.5H17h2.5a1.5 1.5 0 0 1 1.5 1.5v7a1.5 1.5 0 0 1-1.5 1.5z" /></>,
  more: <><circle cx="5.5" cy="12" r="1" /><circle cx="12" cy="12" r="1" /><circle cx="18.5" cy="12" r="1" /></>,
  close: <path d="M6 6l12 12M18 6 6 18" />,
  chevronLeft: <path d="m15 5-7 7 7 7" />,
  chevronRight: <path d="m9 5 7 7-7 7" />,
  clock: <><circle cx="12" cy="12" r="9" /><path d="M12 7v5l3 2" /></>,
  check: <path d="m5 12.5 4.5 4.5L19 7.5" />,
  flag: <><path d="M5 21V4" /><path d="M5 4h11l-2 4 2 4H5" /></>,
  eye: <><path d="M2.5 12S6 5.5 12 5.5 21.5 12 21.5 12 18 18.5 12 18.5 2.5 12 2.5 12z" /><circle cx="12" cy="12" r="3" /></>,
  lock: <><rect x="5" y="10.5" width="14" height="10" rx="2" /><path d="M8 10.5V8a4 4 0 0 1 8 0v2.5" /></>,
  upload: <><path d="M12 15.5V4M7 8.5 12 4l5 4.5" /><path d="M4 15v3.5A2.5 2.5 0 0 0 6.5 21h11a2.5 2.5 0 0 0 2.5-2.5V15" /></>,
  image: <><rect x="3" y="3.5" width="18" height="17" rx="2.5" /><circle cx="9" cy="9.5" r="1.8" /><path d="m21 16-5-5-9.5 9.5" /></>,
  film: <><rect x="3" y="3.5" width="18" height="17" rx="2.5" /><path d="M7.5 3.5v17M16.5 3.5v17M3 9h4.5M3 15h4.5M16.5 9H21M16.5 15H21" /></>,
  layers: <><path d="m12 3 9 5-9 5-9-5z" /><path d="m3 13 9 5 9-5" /></>,
  music: <><path d="M9 18V5.5l11-2V16" /><circle cx="6.5" cy="18" r="2.5" /><circle cx="17.5" cy="16" r="2.5" /></>,
  hash: <path d="M5 9h15M4 15h15M10 3.5 8 20.5M16 3.5l-2 17" />,
  inbox: <><path d="M3 13.5 5.5 5h13l2.5 8.5V19a1.5 1.5 0 0 1-1.5 1.5h-15A1.5 1.5 0 0 1 3 19z" /><path d="M3 13.5h5l1.5 2.5h5l1.5-2.5h5" /></>,
  sparkle: <path d="M12 3.5 13.8 10l6.7 2-6.7 2L12 20.5 10.2 14l-6.7-2 6.7-2z" />,
};

export function Icon({ name, size = 20, filled = false, strokeWidth = 1.75, className = "", title }) {
  return (
    <svg width={size} height={size} viewBox="0 0 24 24" fill={filled ? "currentColor" : "none"} stroke="currentColor"
      strokeWidth={strokeWidth} strokeLinecap="round" strokeLinejoin="round" className={`shrink-0 ${className}`}
      aria-hidden={title ? undefined : "true"} role={title ? "img" : undefined}>
      {title && <title>{title}</title>}
      {PATHS[name] ?? PATHS.sparkle}
    </svg>
  );
}
