// Tiny progressive enhancements for the landing page (it works fully without JavaScript).
document.getElementById("year").textContent = new Date().getFullYear();

// Highlight the nav link of the section currently on screen (IntersectionObserver).
const links = new Map([...document.querySelectorAll('.nav-links a[href^="#"]')].map((a) => [a.hash.slice(1), a]));
const observer = new IntersectionObserver((entries) => {
  for (const entry of entries) {
    const link = links.get(entry.target.id);
    if (link) link.toggleAttribute("aria-current", entry.isIntersecting);
  }
}, { rootMargin: "-45% 0px -50% 0px" });
links.forEach((_, id) => {
  const section = document.getElementById(id);
  if (section) observer.observe(section);
});
