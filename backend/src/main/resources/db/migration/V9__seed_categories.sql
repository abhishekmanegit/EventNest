-- =============================================================================
-- V9: category taxonomy
-- =============================================================================
-- Reference data, so it belongs in SQL: it is identical in every environment and
-- has no dependency on anything else. Accounts are NOT seeded here - a
-- password hash must be produced by Spring's BCryptPasswordEncoder, never
-- hardcoded in a migration. See DevDataInitializer (dev profile only).

INSERT INTO categories (name, slug, description, icon, color_hex, display_order) VALUES
('Hackathon',              'hackathon',              'Build something in a limited time, usually with prizes and mentors',        'Code2',        '#4338CA', 10),
('Coding Competition',     'coding-competition',     'Algorithmic or competitive programming contests',                        'Terminal',     '#0F766E', 20),
('Project Competition',    'project-competition',    'Build and present a working project, hardware or software',                'Lightbulb',    '#B45309', 30),
('Paper Presentation',     'paper-presentation',     'Present research, survey work or an academic paper',                       'FileText',     '#7C3AED', 40),
('Workshop',               'workshop',               'Hands-on, skill-focused sessions taught by practitioners',                 'Wrench',       '#0891B2', 50),
('Seminar',                'seminar',                'Talks, panel discussions and expert sessions',                             'Presentation', '#4D7C0F', 60),
('Cultural',               'cultural',               'Festivals, performances, dance, music and drama',                         'Music4',       '#BE185D', 70),
('Sports',                 'sports',                 'Tournaments, athletics, cricket, football, basketball',                   'Trophy',       '#15803D', 80),
('Robotics',               'robotics',               'Robot builds, autonomous tasks and hardware challenges',                   'Bot',          '#0369A1', 90),
('Entrepreneurship',       'entrepreneurship',       'Startup pitches, business plan and idea competitions',                   'Rocket',       '#C2410C', 100),
('Quiz',                   'quiz',                   'General knowledge, technical or business quizzes',                        'CircleHelp',   '#CA8A04', 110),
('Debate',                 'debate',                 'Parliamentary, policy and motion-based debates',                         'MessageSquare','#A21CAF', 120),
('Photography',            'photography',            'Photo contests, exhibitions and workshops',                               'Camera',       '#0F766E', 130),
('Design',                 'design',                 'UI/UX, graphic design, branding and creative challenges',                 'Palette',      '#9333EA', 140),
('Other',                  'other',                  'Anything that does not fit the categories above',                         'Sparkles',     '#52525B', 999);

-- Analytics ("Hackathons: 12") counts events by category. Counting once here
-- keeps the home page and the /categories API to a single cheap query instead
-- of one COUNT per category on every page load.
--
-- The column must exist BEFORE the function is created: PostgreSQL validates a
-- function body at CREATE time (check_function_bodies), so a function referring
-- to a column that does not exist yet is rejected immediately.
ALTER TABLE categories ADD COLUMN event_count INTEGER NOT NULL DEFAULT 0;

CREATE OR REPLACE FUNCTION refresh_category_event_counts()
RETURNS void AS $$
    UPDATE categories c
       SET event_count = COALESCE(agg.cnt, 0)
      FROM (SELECT category_id, COUNT(*) AS cnt
              FROM events
             WHERE status = 'PUBLISHED'
             GROUP BY category_id) agg
     WHERE agg.category_id = c.id;
$$ LANGUAGE sql;

SELECT refresh_category_event_counts();
