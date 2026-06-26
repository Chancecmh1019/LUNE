// ========================================
// Scroll Progress Bar
// ========================================

const progressBar = document.createElement('div');
progressBar.className = 'scroll-progress';
document.body.appendChild(progressBar);

function updateScrollProgress() {
    const scrollTop = window.pageYOffset || document.documentElement.scrollTop;
    const scrollHeight = document.documentElement.scrollHeight - document.documentElement.clientHeight;
    const scrollProgress = (scrollTop / scrollHeight) * 100;
    progressBar.style.width = scrollProgress + '%';
}

window.addEventListener('scroll', updateScrollProgress);

// ========================================
// Navbar Scroll Effects
// ========================================

const navbar = document.getElementById('navbar');

window.addEventListener('scroll', () => {
    const scrollTop = window.pageYOffset || document.documentElement.scrollTop;
    
    if (scrollTop > 50) {
        navbar.classList.add('scrolled');
    } else {
        navbar.classList.remove('scrolled');
    }
});

// ========================================
// Smooth Scroll
// ========================================

document.querySelectorAll('a[href^="#"]').forEach(anchor => {
    anchor.addEventListener('click', function (e) {
        e.preventDefault();
        const target = document.querySelector(this.getAttribute('href'));
        if (target) {
            const offset = 80;
            const targetPosition = target.offsetTop - offset;
            window.scrollTo({
                top: targetPosition,
                behavior: 'smooth'
            });
        }
    });
});

// ========================================
// Intersection Observer for Scroll Animations
// ========================================

const observerOptions = {
    threshold: 0.15,
    rootMargin: '0px 0px -100px 0px'
};

const observer = new IntersectionObserver((entries) => {
    entries.forEach(entry => {
        if (entry.isIntersecting) {
            entry.target.classList.add('visible');
            observer.unobserve(entry.target);
        }
    });
}, observerOptions);

// Observe elements
document.querySelectorAll('.feature-card, .section-header, .about-content').forEach(el => {
    observer.observe(el);
});

// ========================================
// Language Toggle with Persistence
// ========================================

let currentLang = localStorage.getItem('lune-lang') || 'en';

function toggleLanguage() {
    currentLang = currentLang === 'en' ? 'zh' : 'en';
    localStorage.setItem('lune-lang', currentLang);
    updateLanguage();
}

function updateLanguage() {
    const elements = document.querySelectorAll('[data-en][data-zh]');
    elements.forEach(el => {
        if (el.tagName === 'INPUT' || el.tagName === 'TEXTAREA') {
            el.placeholder = el.getAttribute(`data-${currentLang}`);
        } else {
            el.textContent = el.getAttribute(`data-${currentLang}`);
        }
    });
    
    // Update language switch button
    const langSwitch = document.querySelector('.lang-text');
    if (langSwitch) {
        langSwitch.textContent = currentLang === 'en' ? '繁體中文' : 'English';
    }
    
    // Update HTML lang attribute
    document.documentElement.lang = currentLang === 'en' ? 'en' : 'zh-TW';
}

// Initialize language on page load
document.addEventListener('DOMContentLoaded', () => {
    updateLanguage();
});

// ========================================
// Page Transition Effects
// ========================================

// Smooth fade-in on page load
window.addEventListener('load', () => {
    document.body.classList.add('loaded');
});

// Smooth fade-out on page navigation
document.querySelectorAll('a[href$=".html"]').forEach(link => {
    link.addEventListener('click', function(e) {
        const href = this.getAttribute('href');
        if (href && !href.startsWith('#')) {
            e.preventDefault();
            document.body.style.opacity = '0';
            document.body.style.transition = 'opacity 0.3s ease';
            setTimeout(() => {
                window.location.href = href;
            }, 300);
        }
    });
});

console.log('🌙 LUNE Website - Made by Chancecmh1019');
