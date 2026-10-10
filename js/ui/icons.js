/* Zestaw ikon interfejsu (SVG, kolor dziedziczony z currentColor). */
(function (P) {
  'use strict';

  const s = (body, extra) => '<svg viewBox="0 0 24 24" aria-hidden="true" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"' + (extra || '') + '>' + body + '</svg>';

  P.Icons = {
    bowl: s('<path d="M3 11h18a9 9 0 0 1-18 0z"/><path d="M8 11c0-2 1.5-3.5 4-3.5S16 9 16 11"/><path d="M7 20h10"/>'),
    drop: s('<path d="M12 3s6 6.5 6 11a6 6 0 0 1-12 0c0-4.5 6-11 6-11z"/><path d="M9.5 14.5a2.5 2.5 0 0 0 2.5 2.5"/>'),
    ball: s('<circle cx="12" cy="12" r="9"/><path d="M5.6 5.6c3.2 3.2 3.2 9.6 0 12.8M18.4 5.6c-3.2 3.2-3.2 9.6 0 12.8"/>'),
    treat: s('<path d="M7 9a2.5 2.5 0 1 1 3-3.5l4 4A2.5 2.5 0 1 1 17.5 13a2.5 2.5 0 1 1-3.5 3l-4-4A2.5 2.5 0 1 1 7 9z" transform="rotate(0 12 12)"/>'),
    leash: s('<circle cx="7" cy="17" r="3"/><path d="M9.5 15.5 19 6"/><path d="M16 4h4v4"/>'),
    bath: s('<path d="M4 12h16v2a6 6 0 0 1-6 6h-4a6 6 0 0 1-6-6z"/><path d="M6 12V6a2 2 0 0 1 4 0"/><circle cx="14" cy="6" r="1.2"/><circle cx="17.5" cy="8.5" r="1"/>'),
    bone: s('<path d="M8.5 6.5a2.5 2.5 0 1 0-3 3l9 9a2.5 2.5 0 1 0 3-3 2.5 2.5 0 1 0-3-3l-3-3a2.5 2.5 0 1 0-3-3z"/>'),
    bulb: s('<path d="M9 18h6M10 21h4"/><path d="M12 3a6 6 0 0 0-4 10.5c.8.8 1 1.5 1 2.5h6c0-1 .2-1.7 1-2.5A6 6 0 0 0 12 3z"/>'),
    moon: s('<path d="M20 14.5A8 8 0 1 1 9.5 4a6.5 6.5 0 0 0 10.5 10.5z"/>'),
    star: s('<path d="m12 3 2.7 5.6 6.1.9-4.4 4.3 1 6.1L12 17l-5.4 2.9 1-6.1-4.4-4.3 6.1-.9z"/>'),
    bag: s('<path d="M5 8h14l-1 12H6z"/><path d="M9 8V6a3 3 0 0 1 6 0v2"/>'),
    shop: s('<path d="M4 9h16l-1.5-5h-13z"/><path d="M5 9v11h14V9"/><path d="M10 20v-5h4v5"/>'),
    palette: s('<path d="M12 3a9 9 0 1 0 0 18c1.2 0 2-.8 2-1.8 0-1.4-1.2-1.7-1.2-3 0-1 .8-1.7 1.9-1.7H17a4 4 0 0 0 4-4C21 6.4 17 3 12 3z"/><circle cx="7.5" cy="11" r="1"/><circle cx="10" cy="7" r="1"/><circle cx="14.5" cy="7" r="1"/>'),
    user: s('<circle cx="12" cy="8" r="4"/><path d="M4 21a8 8 0 0 1 16 0"/>'),
    heart: s('<path d="M12 20s-7-4.4-7-10a4 4 0 0 1 7-2.6A4 4 0 0 1 19 10c0 5.6-7 10-7 10z"/>', ' fill="currentColor"'),
    coin: s('<circle cx="12" cy="12" r="8.5"/><path d="M12 7.5v9M9.5 9.5h3.75a1.75 1.75 0 0 1 0 3.5h-2.5a1.75 1.75 0 0 0 0 3.5H14.5"/>'),
    bolt: s('<path d="M13 3 5 14h6l-1 7 8-11h-6z"/>'),
    home: s('<path d="M4 11 12 4l8 7"/><path d="M6 10v10h12V10"/>'),
    close: s('<path d="M6 6l12 12M18 6 6 18"/>'),
    check: s('<path d="m5 12.5 4.5 4.5L19 7.5"/>'),
    paw: s('<ellipse cx="12" cy="16" rx="4" ry="3.3"/><circle cx="6.5" cy="10.5" r="1.8"/><circle cx="10" cy="7" r="1.8"/><circle cx="14" cy="7" r="1.8"/><circle cx="17.5" cy="10.5" r="1.8"/>'),
    lock: s('<rect x="5" y="11" width="14" height="9" rx="2"/><path d="M8 11V8a4 4 0 0 1 8 0v3"/>'),
    stick: s('<path d="M4 18 19 7"/><path d="M13 11.5 16 15"/>'),
    collar: s('<ellipse cx="12" cy="11" rx="8" ry="4"/><circle cx="12" cy="17.5" r="2"/>'),
    bandana: s('<path d="M3 8h18l-8 12z"/>'),
    bowtie: s('<path d="M3 7v10l7-3.5V10.5zM21 7v10l-7-3.5V10.5z"/><rect x="10" y="10" width="4" height="4" rx="1"/>'),
    bow: s('<path d="M12 12c-3-4-8-5-8-1s5 3 8 1zm0 0c3-4 8-5 8-1s-5 3-8 1z"/><circle cx="12" cy="12" r="1.6"/>'),
    cap: s('<path d="M4 15a8 8 0 0 1 16 0z"/><path d="M14 15h7"/>'),
    sparkle: s('<path d="M12 3v4M12 17v4M3 12h4M17 12h4M6 6l2.5 2.5M15.5 15.5 18 18M6 18l2.5-2.5M15.5 8.5 18 6"/>'),
    gear: s('<circle cx="12" cy="12" r="3"/><path d="M19.4 15a1.6 1.6 0 0 0 .3 1.8l.1.1a2 2 0 1 1-2.8 2.8l-.1-.1a1.6 1.6 0 0 0-1.8-.3 1.6 1.6 0 0 0-1 1.5V21a2 2 0 0 1-4 0v-.1a1.6 1.6 0 0 0-1-1.5 1.6 1.6 0 0 0-1.8.3l-.1.1a2 2 0 1 1-2.8-2.8l.1-.1a1.6 1.6 0 0 0 .3-1.8 1.6 1.6 0 0 0-1.5-1H3a2 2 0 0 1 0-4h.1a1.6 1.6 0 0 0 1.5-1 1.6 1.6 0 0 0-.3-1.8l-.1-.1a2 2 0 1 1 2.8-2.8l.1.1a1.6 1.6 0 0 0 1.8.3H9a1.6 1.6 0 0 0 1-1.5V3a2 2 0 0 1 4 0v.1a1.6 1.6 0 0 0 1 1.5 1.6 1.6 0 0 0 1.8-.3l.1-.1a2 2 0 1 1 2.8 2.8l-.1.1a1.6 1.6 0 0 0-.3 1.8V9a1.6 1.6 0 0 0 1.5 1H21a2 2 0 0 1 0 4h-.1a1.6 1.6 0 0 0-1.5 1z"/>'),
    flip: s('<path d="M4 7h13l-3-3M20 17H7l3 3"/>'),
    play: s('<path d="M7 5v14l11-7z"/>', ' fill="currentColor"'),
  };
})(window.Pies = window.Pies || {});
