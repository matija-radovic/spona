-- Demo data for the dev profile, run by DevSeeder. Not a migration.
-- Passwords are written by DevSeeder after this script.

-- Actors

INSERT INTO actor (id, actor_type, created_at) VALUES
  ('5eed0001-0000-4000-8000-000000000001', 'user', now() - interval '90 days'),
  ('5eed0001-0000-4000-8000-000000000002', 'user', now() - interval '85 days'),
  ('5eed0001-0000-4000-8000-000000000003', 'user', now() - interval '80 days'),
  ('5eed0001-0000-4000-8000-000000000004', 'user', now() - interval '70 days'),
  ('5eed0001-0000-4000-8000-000000000005', 'user', now() - interval '65 days'),
  ('5eed0001-0000-4000-8000-000000000006', 'user', now() - interval '20 days'),
  ('5eed0001-0000-4000-8000-000000000007', 'user', now() - interval '60 days'),
  ('5eed0001-0000-4000-8000-000000000008', 'user', now() - interval '1 day'),
  ('5eed0002-0000-4000-8000-000000000001', 'org',  now() - interval '60 days'),
  ('5eed0002-0000-4000-8000-000000000002', 'org',  now() - interval '45 days'),
  ('5eed0002-0000-4000-8000-000000000003', 'org',  now() - interval '35 days');

INSERT INTO app_user (id, username, full_name, email, password_digest, email_verified_at, bio, location, other_contact_info) VALUES
  ('5eed0001-0000-4000-8000-000000000001', 'ana-petrovic', 'Ana Petrović', 'ana-petrovic@seed.spona.local', '-', now() - interval '90 days',
   'Full-stack programerka i osnivačica Kodnesta. Pišem o Javi, Reactu i testiranju.', 'Beograd',
   '[{"type":"linkedin","value":"https://www.linkedin.com/in/ana-petrovic-demo"}]'),
  ('5eed0001-0000-4000-8000-000000000002', 'marko-jovanovic', 'Marko Jovanović', 'marko-jovanovic@seed.spona.local', '-', now() - interval '85 days',
   'Agronom, vodim Zelenu baštu i pomažem Kodnestu oko poslovanja.', 'Novi Sad', '[]'),
  ('5eed0001-0000-4000-8000-000000000003', 'jelena-nikolic', 'Jelena Nikolić', 'jelena-nikolic@seed.spona.local', '-', now() - interval '80 days',
   'Regruterka sa deset godina iskustva u IT-u i zdravstvu.', 'Niš',
   '[{"type":"email","value":"jelena.nikolic@example.com","description":"poslovni"}]'),
  ('5eed0001-0000-4000-8000-000000000004', 'stefan-ilic', 'Stefan Ilić', 'stefan-ilic@seed.spona.local', '-', now() - interval '70 days',
   'UX dizajner i ilustrator.', 'Kragujevac',
   '[{"type":"website","value":"https://stefan-ilic.example"},{"type":"instagram","value":"https://www.instagram.com/stefan.ilic.demo"}]'),
  ('5eed0001-0000-4000-8000-000000000005', 'milica-djordjevic', 'Milica Đorđević', 'milica-djordjevic@seed.spona.local', '-', now() - interval '65 days',
   'Marketing i komunikacije u Kodnestu.', 'Beograd', '[]'),
  ('5eed0001-0000-4000-8000-000000000006', 'nikola-stojanovic', 'Nikola Stojanović', 'nikola-stojanovic@seed.spona.local', '-', now() - interval '20 days',
   'Student završne godine elektrotehnike.', 'Subotica', '[]'),
  ('5eed0001-0000-4000-8000-000000000007', 'ivana-markovic', 'Ivana Marković', 'ivana-markovic@seed.spona.local', '-', now() - interval '60 days',
   'Doktorka pedijatrije, direktorka Klinike Sunce.', 'Čačak',
   '[{"type":"phone","value":"+381 32 555 011","description":"ordinacija"}]'),
  ('5eed0001-0000-4000-8000-000000000008', 'luka-pavlovic', '', 'luka-pavlovic@seed.spona.local', '-', now() - interval '1 day',
   NULL, NULL, '[]');

INSERT INTO organization (id, slug, display_name, legal_name, description, website, location, other_contact_info) VALUES
  ('5eed0002-0000-4000-8000-000000000001', 'kodnest', 'Kodnest', 'Kodnest d.o.o. Beograd',
   'Softverska kuća koja gradi web i mobilne aplikacije za domaće i strane klijente.', 'https://kodnest.example', 'Beograd',
   '[{"type":"email","value":"kontakt@kodnest.example"},{"type":"phone","value":"+381 11 555 0100","description":"recepcija"}]'),
  ('5eed0002-0000-4000-8000-000000000002', 'zelena-basta', 'Zelena bašta', 'Zelena bašta d.o.o. Novi Sad',
   'Porodično gazdinstvo koje uzgaja maline, jagode i povrće na četrdeset hektara.', NULL, 'Novi Sad',
   '[{"type":"phone","value":"+381 21 555 0142"}]'),
  ('5eed0002-0000-4000-8000-000000000003', 'klinika-sunce', 'Klinika Sunce', 'Zdravstvena ustanova Klinika Sunce Čačak',
   'Privatna klinika opšte medicine i pedijatrije.', 'https://klinika-sunce.example', 'Čačak', '[]');

-- Memberships, roles and the invitations that brought people in

INSERT INTO organization_member (user_id, organization_id, joined_at) VALUES
  ('5eed0001-0000-4000-8000-000000000001', '5eed0002-0000-4000-8000-000000000001', now() - interval '60 days'),
  ('5eed0001-0000-4000-8000-000000000002', '5eed0002-0000-4000-8000-000000000001', now() - interval '50 days'),
  ('5eed0001-0000-4000-8000-000000000003', '5eed0002-0000-4000-8000-000000000001', now() - interval '40 days'),
  ('5eed0001-0000-4000-8000-000000000005', '5eed0002-0000-4000-8000-000000000001', now() - interval '30 days'),
  ('5eed0001-0000-4000-8000-000000000002', '5eed0002-0000-4000-8000-000000000002', now() - interval '45 days'),
  ('5eed0001-0000-4000-8000-000000000004', '5eed0002-0000-4000-8000-000000000002', now() - interval '20 days'),
  ('5eed0001-0000-4000-8000-000000000007', '5eed0002-0000-4000-8000-000000000003', now() - interval '35 days'),
  ('5eed0001-0000-4000-8000-000000000003', '5eed0002-0000-4000-8000-000000000003', now() - interval '25 days');

INSERT INTO role_assignment (user_id, organization_id, role, assigned_at, assigned_by) VALUES
  ('5eed0001-0000-4000-8000-000000000001', '5eed0002-0000-4000-8000-000000000001', 'OWNER',     now() - interval '60 days', '5eed0001-0000-4000-8000-000000000001'),
  ('5eed0001-0000-4000-8000-000000000002', '5eed0002-0000-4000-8000-000000000001', 'ADMIN',     now() - interval '50 days', '5eed0001-0000-4000-8000-000000000001'),
  ('5eed0001-0000-4000-8000-000000000003', '5eed0002-0000-4000-8000-000000000001', 'RECRUITER', now() - interval '40 days', '5eed0001-0000-4000-8000-000000000001'),
  ('5eed0001-0000-4000-8000-000000000005', '5eed0002-0000-4000-8000-000000000001', 'EDITOR',    now() - interval '30 days', '5eed0001-0000-4000-8000-000000000002'),
  ('5eed0001-0000-4000-8000-000000000002', '5eed0002-0000-4000-8000-000000000002', 'OWNER',     now() - interval '45 days', '5eed0001-0000-4000-8000-000000000002'),
  ('5eed0001-0000-4000-8000-000000000004', '5eed0002-0000-4000-8000-000000000002', 'PUBLISHER', now() - interval '20 days', '5eed0001-0000-4000-8000-000000000002'),
  ('5eed0001-0000-4000-8000-000000000007', '5eed0002-0000-4000-8000-000000000003', 'OWNER',     now() - interval '35 days', '5eed0001-0000-4000-8000-000000000007'),
  ('5eed0001-0000-4000-8000-000000000003', '5eed0002-0000-4000-8000-000000000003', 'RECRUITER', now() - interval '25 days', '5eed0001-0000-4000-8000-000000000007');

INSERT INTO organization_invitations (organization_id, role, invitee_user_id, invitee_email, invited_by_id, status, created_at, resolved_at) VALUES
  ('5eed0002-0000-4000-8000-000000000001', 'ADMIN',     '5eed0001-0000-4000-8000-000000000002', 'marko-jovanovic@seed.spona.local',   '5eed0001-0000-4000-8000-000000000001', 'ACCEPTED', now() - interval '51 days', now() - interval '50 days'),
  ('5eed0002-0000-4000-8000-000000000001', 'RECRUITER', '5eed0001-0000-4000-8000-000000000003', 'jelena-nikolic@seed.spona.local',    '5eed0001-0000-4000-8000-000000000001', 'ACCEPTED', now() - interval '41 days', now() - interval '40 days'),
  ('5eed0002-0000-4000-8000-000000000001', 'EDITOR',    '5eed0001-0000-4000-8000-000000000005', 'milica-djordjevic@seed.spona.local', '5eed0001-0000-4000-8000-000000000002', 'ACCEPTED', now() - interval '31 days', now() - interval '30 days'),
  ('5eed0002-0000-4000-8000-000000000002', 'PUBLISHER', '5eed0001-0000-4000-8000-000000000004', 'stefan-ilic@seed.spona.local',       '5eed0001-0000-4000-8000-000000000002', 'ACCEPTED', now() - interval '22 days', now() - interval '20 days'),
  ('5eed0002-0000-4000-8000-000000000003', 'RECRUITER', '5eed0001-0000-4000-8000-000000000003', 'jelena-nikolic@seed.spona.local',    '5eed0001-0000-4000-8000-000000000007', 'ACCEPTED', now() - interval '26 days', now() - interval '25 days'),
  ('5eed0002-0000-4000-8000-000000000001', 'EDITOR',    '5eed0001-0000-4000-8000-000000000006', 'nikola-stojanovic@seed.spona.local', '5eed0001-0000-4000-8000-000000000001', 'WAITING',  now() - interval '2 days',  NULL),
  ('5eed0002-0000-4000-8000-000000000003', 'RECRUITER', NULL,                                   'dragana.simic@example.com',          '5eed0001-0000-4000-8000-000000000007', 'WAITING',  now() - interval '3 days',  NULL);

INSERT INTO follows (follower_id, followee_id, created_at) VALUES
  ('5eed0001-0000-4000-8000-000000000001', '5eed0001-0000-4000-8000-000000000002', now() - interval '80 days'),
  ('5eed0001-0000-4000-8000-000000000001', '5eed0002-0000-4000-8000-000000000002', now() - interval '40 days'),
  ('5eed0001-0000-4000-8000-000000000002', '5eed0001-0000-4000-8000-000000000001', now() - interval '80 days'),
  ('5eed0001-0000-4000-8000-000000000002', '5eed0002-0000-4000-8000-000000000001', now() - interval '50 days'),
  ('5eed0001-0000-4000-8000-000000000003', '5eed0002-0000-4000-8000-000000000001', now() - interval '40 days'),
  ('5eed0001-0000-4000-8000-000000000003', '5eed0002-0000-4000-8000-000000000003', now() - interval '25 days'),
  ('5eed0001-0000-4000-8000-000000000004', '5eed0001-0000-4000-8000-000000000001', now() - interval '60 days'),
  ('5eed0001-0000-4000-8000-000000000004', '5eed0002-0000-4000-8000-000000000001', now() - interval '15 days'),
  ('5eed0001-0000-4000-8000-000000000005', '5eed0002-0000-4000-8000-000000000001', now() - interval '30 days'),
  ('5eed0001-0000-4000-8000-000000000005', '5eed0001-0000-4000-8000-000000000004', now() - interval '12 days'),
  ('5eed0001-0000-4000-8000-000000000006', '5eed0002-0000-4000-8000-000000000001', now() - interval '6 days'),
  ('5eed0001-0000-4000-8000-000000000006', '5eed0001-0000-4000-8000-000000000001', now() - interval '6 days'),
  ('5eed0001-0000-4000-8000-000000000006', '5eed0002-0000-4000-8000-000000000002', now() - interval '4 days'),
  ('5eed0001-0000-4000-8000-000000000007', '5eed0001-0000-4000-8000-000000000003', now() - interval '26 days'),
  ('5eed0001-0000-4000-8000-000000000008', '5eed0002-0000-4000-8000-000000000003', now() - interval '12 hours'),
  ('5eed0001-0000-4000-8000-000000000008', '5eed0001-0000-4000-8000-000000000006', now() - interval '10 hours');

-- Posts, comments and reactions

INSERT INTO post (id, content, created_by_id, created_for_id, created_at) VALUES
  ('5eed0003-0000-4000-8000-000000000001', 'Kodnest zapošljava! Tražimo Java programera, UX dizajnera i studente za praksu. Detalji su u oglasima na našem profilu.',
   '5eed0001-0000-4000-8000-000000000001', '5eed0002-0000-4000-8000-000000000001', now() - interval '6 days'),
  ('5eed0003-0000-4000-8000-000000000002', 'Posle tri godine rada sa Spring Boot-om, najveća lekcija: testovi nad pravom bazom otkrivaju greške koje mock nikad ne pokaže.',
   '5eed0001-0000-4000-8000-000000000001', '5eed0001-0000-4000-8000-000000000001', now() - interval '5 days'),
  ('5eed0003-0000-4000-8000-000000000003', 'Počinje sezona berbe malina. Ove godine očekujemo rekordan rod i tražimo sezonske radnike iz okoline Novog Sada.',
   '5eed0001-0000-4000-8000-000000000002', '5eed0002-0000-4000-8000-000000000002', now() - interval '4 days 20 hours'),
  ('5eed0003-0000-4000-8000-000000000004', 'Završio sam redizajn aplikacije za lokalnu biblioteku. Najviše vremena otišlo je na pristupačnost i kontrast boja.',
   '5eed0001-0000-4000-8000-000000000004', '5eed0001-0000-4000-8000-000000000004', now() - interval '4 days'),
  ('5eed0003-0000-4000-8000-000000000005', 'Naš tim je ovog vikenda bio na hakatonu u Novom Sadu i osvojio drugo mesto. Hvala svima koji su navijali!',
   '5eed0001-0000-4000-8000-000000000005', '5eed0002-0000-4000-8000-000000000001', now() - interval '3 days'),
  ('5eed0003-0000-4000-8000-000000000006', 'Klinika Sunce otvara novu ambulantu za decu. Tražimo medicinske sestre i tehničare sa iskustvom u pedijatriji.',
   '5eed0001-0000-4000-8000-000000000007', '5eed0002-0000-4000-8000-000000000003', now() - interval '2 days 6 hours'),
  ('5eed0003-0000-4000-8000-000000000007', 'Student sam završne godine elektrotehnike i tražim praksu u oblasti razvoja softvera. Svaki savet je dobrodošao.',
   '5eed0001-0000-4000-8000-000000000006', '5eed0001-0000-4000-8000-000000000006', now() - interval '1 day 20 hours'),
  ('5eed0003-0000-4000-8000-000000000008', 'Savet za CV: prvo navedite rezultate, pa tek onda odgovornosti. Regruter vaš CV pregleda za manje od minuta.',
   '5eed0001-0000-4000-8000-000000000003', '5eed0001-0000-4000-8000-000000000003', now() - interval '1 day'),
  ('5eed0003-0000-4000-8000-000000000009', 'Prvi dan na Sponi. Pozdrav svima!',
   '5eed0001-0000-4000-8000-000000000008', '5eed0001-0000-4000-8000-000000000008', now() - interval '10 hours'),
  ('5eed0003-0000-4000-8000-000000000010', 'Da li neko ima iskustva sa hibridnim radom u poljoprivredi? Deo posla je na terenu, a deo u kancelariji.',
   '5eed0001-0000-4000-8000-000000000002', '5eed0001-0000-4000-8000-000000000002', now() - interval '3 hours');

INSERT INTO comment (id, content, response_to_comment_id, part_of_post_id, created_by_id, created_for_id, created_at) VALUES
  ('5eed0004-0000-4000-8000-000000000001', 'Da li je praksa otvorena i za studente iz drugih gradova?', NULL,
   '5eed0003-0000-4000-8000-000000000001', '5eed0001-0000-4000-8000-000000000006', '5eed0001-0000-4000-8000-000000000006', now() - interval '5 days 20 hours'),
  ('5eed0004-0000-4000-8000-000000000002', 'Jeste, prvi mesec je moguć i rad na daljinu. Pošaljite CV preko oglasa.', '5eed0004-0000-4000-8000-000000000001',
   '5eed0003-0000-4000-8000-000000000001', '5eed0001-0000-4000-8000-000000000001', '5eed0002-0000-4000-8000-000000000001', now() - interval '5 days 18 hours'),
  ('5eed0004-0000-4000-8000-000000000003', 'Prijavio sam se za dizajnersku poziciju, srećno svima!', NULL,
   '5eed0003-0000-4000-8000-000000000001', '5eed0001-0000-4000-8000-000000000004', '5eed0001-0000-4000-8000-000000000004', now() - interval '5 days 10 hours'),
  ('5eed0004-0000-4000-8000-000000000004', 'Potpuno se slažem. Testcontainers nam je uštedeo mnogo vremena.', NULL,
   '5eed0003-0000-4000-8000-000000000002', '5eed0001-0000-4000-8000-000000000002', '5eed0001-0000-4000-8000-000000000002', now() - interval '4 days 22 hours'),
  ('5eed0004-0000-4000-8000-000000000005', 'Tačno tako, i to bez ikakvog podešavanja na mašini.', '5eed0004-0000-4000-8000-000000000004',
   '5eed0003-0000-4000-8000-000000000002', '5eed0001-0000-4000-8000-000000000001', '5eed0001-0000-4000-8000-000000000001', now() - interval '4 days 21 hours'),
  ('5eed0004-0000-4000-8000-000000000006', 'Koju knjigu biste preporučili za početak sa Springom?', NULL,
   '5eed0003-0000-4000-8000-000000000002', '5eed0001-0000-4000-8000-000000000006', '5eed0001-0000-4000-8000-000000000006', now() - interval '4 days 12 hours'),
  ('5eed0004-0000-4000-8000-000000000007', 'Da li je obezbeđen prevoz do plantaže?', NULL,
   '5eed0003-0000-4000-8000-000000000003', '5eed0001-0000-4000-8000-000000000008', '5eed0001-0000-4000-8000-000000000008', now() - interval '9 hours'),
  ('5eed0004-0000-4000-8000-000000000008', 'Jeste, prevoz polazi iz centra Novog Sada svakog jutra u šest.', '5eed0004-0000-4000-8000-000000000007',
   '5eed0003-0000-4000-8000-000000000003', '5eed0001-0000-4000-8000-000000000004', '5eed0002-0000-4000-8000-000000000002', now() - interval '8 hours'),
  ('5eed0004-0000-4000-8000-000000000009', 'Ponosna sam na ceo tim!', NULL,
   '5eed0003-0000-4000-8000-000000000005', '5eed0001-0000-4000-8000-000000000001', '5eed0001-0000-4000-8000-000000000001', now() - interval '2 days 22 hours'),
  ('5eed0004-0000-4000-8000-000000000010', 'Odlična vest za Čačak.', NULL,
   '5eed0003-0000-4000-8000-000000000006', '5eed0001-0000-4000-8000-000000000003', '5eed0001-0000-4000-8000-000000000003', now() - interval '2 days'),
  ('5eed0004-0000-4000-8000-000000000011', 'Pogledaj oglas za praksu u Kodnestu, rok je do kraja meseca.', NULL,
   '5eed0003-0000-4000-8000-000000000007', '5eed0001-0000-4000-8000-000000000003', '5eed0001-0000-4000-8000-000000000003', now() - interval '1 day 10 hours'),
  ('5eed0004-0000-4000-8000-000000000012', 'Hvala, upravo sam se prijavio.', '5eed0004-0000-4000-8000-000000000011',
   '5eed0003-0000-4000-8000-000000000007', '5eed0001-0000-4000-8000-000000000006', '5eed0001-0000-4000-8000-000000000006', now() - interval '1 day 8 hours'),
  ('5eed0004-0000-4000-8000-000000000013', 'Ovo bih volela da sam znala pre pet godina.', NULL,
   '5eed0003-0000-4000-8000-000000000008', '5eed0001-0000-4000-8000-000000000005', '5eed0001-0000-4000-8000-000000000005', now() - interval '20 hours');

INSERT INTO reaction (emoji, target_type_id, target_id, created_by_id)
SELECT r.emoji, t.target_type_id, r.target_id::uuid, r.created_by_id::uuid
  FROM (VALUES
    ('posts', '5eed0003-0000-4000-8000-000000000001', '5eed0001-0000-4000-8000-000000000002', '🎉'),
    ('posts', '5eed0003-0000-4000-8000-000000000001', '5eed0001-0000-4000-8000-000000000003', '👍'),
    ('posts', '5eed0003-0000-4000-8000-000000000001', '5eed0001-0000-4000-8000-000000000004', '🔥'),
    ('posts', '5eed0003-0000-4000-8000-000000000001', '5eed0001-0000-4000-8000-000000000005', '🎉'),
    ('posts', '5eed0003-0000-4000-8000-000000000001', '5eed0001-0000-4000-8000-000000000006', '👀'),
    ('posts', '5eed0003-0000-4000-8000-000000000002', '5eed0001-0000-4000-8000-000000000002', '💯'),
    ('posts', '5eed0003-0000-4000-8000-000000000002', '5eed0001-0000-4000-8000-000000000004', '👍'),
    ('posts', '5eed0003-0000-4000-8000-000000000002', '5eed0001-0000-4000-8000-000000000006', '💡'),
    ('posts', '5eed0003-0000-4000-8000-000000000003', '5eed0001-0000-4000-8000-000000000001', '👍'),
    ('posts', '5eed0003-0000-4000-8000-000000000003', '5eed0001-0000-4000-8000-000000000008', '🙌'),
    ('posts', '5eed0003-0000-4000-8000-000000000004', '5eed0001-0000-4000-8000-000000000001', '😍'),
    ('posts', '5eed0003-0000-4000-8000-000000000004', '5eed0001-0000-4000-8000-000000000005', '👏'),
    ('posts', '5eed0003-0000-4000-8000-000000000005', '5eed0001-0000-4000-8000-000000000001', '🎉'),
    ('posts', '5eed0003-0000-4000-8000-000000000005', '5eed0001-0000-4000-8000-000000000002', '🎉'),
    ('posts', '5eed0003-0000-4000-8000-000000000005', '5eed0001-0000-4000-8000-000000000003', '👏'),
    ('posts', '5eed0003-0000-4000-8000-000000000005', '5eed0001-0000-4000-8000-000000000006', '🚀'),
    ('posts', '5eed0003-0000-4000-8000-000000000006', '5eed0001-0000-4000-8000-000000000003', '❤️'),
    ('posts', '5eed0003-0000-4000-8000-000000000006', '5eed0001-0000-4000-8000-000000000008', '👍'),
    ('posts', '5eed0003-0000-4000-8000-000000000007', '5eed0001-0000-4000-8000-000000000001', '🤝'),
    ('posts', '5eed0003-0000-4000-8000-000000000007', '5eed0001-0000-4000-8000-000000000003', '👍'),
    ('posts', '5eed0003-0000-4000-8000-000000000008', '5eed0001-0000-4000-8000-000000000001', '💡'),
    ('posts', '5eed0003-0000-4000-8000-000000000008', '5eed0001-0000-4000-8000-000000000004', '💯'),
    ('posts', '5eed0003-0000-4000-8000-000000000008', '5eed0001-0000-4000-8000-000000000006', '🙏'),
    ('posts', '5eed0003-0000-4000-8000-000000000009', '5eed0001-0000-4000-8000-000000000001', '👍'),
    ('posts', '5eed0003-0000-4000-8000-000000000009', '5eed0001-0000-4000-8000-000000000006', '🙌'),
    ('comments', '5eed0004-0000-4000-8000-000000000002', '5eed0001-0000-4000-8000-000000000006', '🙏'),
    ('comments', '5eed0004-0000-4000-8000-000000000004', '5eed0001-0000-4000-8000-000000000001', '👍'),
    ('comments', '5eed0004-0000-4000-8000-000000000008', '5eed0001-0000-4000-8000-000000000008', '✅'),
    ('comments', '5eed0004-0000-4000-8000-000000000012', '5eed0001-0000-4000-8000-000000000003', '👏')
  ) AS r (table_name, target_id, created_by_id, emoji)
  JOIN reaction_target_types t ON t.table_name = r.table_name;

-- Job postings

INSERT INTO job (id, slug, title, description, keywords, category, job_type, workplace_type, working_hours, experience_level,
                 location, status, application_type, contact_information, external_url, created_by_id, created_for_id, created_at, updated_at) VALUES
  ('5eed0005-0000-4000-8000-000000000001', 'senior-java-programer-4821', 'Senior Java programer',
   'Tražimo iskusnog Java programera za razvoj platforme za zapošljavanje. Radimo sa Spring Boot-om, PostgreSQL-om i Dockerom, a svaki kod pregledamo u paru.',
   ARRAY['java', 'spring boot', 'postgresql', 'docker'], 'IT_SOFTWARE', 'PERMANENT', 'HYBRID', 'FULL_TIME', 'YEARS_5',
   'Beograd', 'OPEN', 'CV_DROP', NULL, NULL,
   '5eed0001-0000-4000-8000-000000000003', '5eed0002-0000-4000-8000-000000000001', now() - interval '6 days', now() - interval '6 days'),
  ('5eed0005-0000-4000-8000-000000000002', 'ux-ui-dizajner-3307', 'UX/UI dizajner',
   'Pridružite se timu koji dizajnira aplikacije za hiljade korisnika. Očekujemo portfolio, znanje Figme i iskustvo sa dizajn sistemima.',
   ARRAY['figma', 'ux', 'dizajn sistem'], 'DESIGN_CREATIVE', 'PERMANENT', 'REMOTE', 'FULL_TIME', 'YEARS_2',
   'Beograd', 'OPEN', 'EXTERNAL_URL', NULL, 'https://kodnest.example/karijera/ux-dizajner',
   '5eed0001-0000-4000-8000-000000000003', '5eed0002-0000-4000-8000-000000000001', now() - interval '5 days', now() - interval '5 days'),
  ('5eed0005-0000-4000-8000-000000000003', 'praksa-u-razvoju-softvera-1590', 'Praksa u razvoju softvera',
   'Tromesečna plaćena praksa za studente završnih godina. Radićete uz mentora na pravom projektu u Reactu i JavaScriptu.',
   ARRAY['praksa', 'javascript', 'react'], 'IT_SOFTWARE', 'INTERNSHIP', 'ONSITE', 'PART_TIME', 'NONE',
   'Beograd', 'OPEN', 'CV_DROP', NULL, NULL,
   '5eed0001-0000-4000-8000-000000000002', '5eed0002-0000-4000-8000-000000000001', now() - interval '3 days', now() - interval '3 days'),
  ('5eed0005-0000-4000-8000-000000000004', 'devops-inzenjer-7712', 'DevOps inženjer',
   'Tražimo inženjera koji će održavati našu infrastrukturu na AWS-u, automatizovati isporuku i brinuti o nadzoru sistema.',
   ARRAY['kubernetes', 'aws', 'terraform'], 'IT_INFRASTRUCTURE', 'PERMANENT', 'HYBRID', 'FULL_TIME', 'YEARS_3',
   'Beograd', 'CLOSED', 'CONTACT_ONLY', '[{"type":"email","value":"karijera@kodnest.example"}]', NULL,
   '5eed0001-0000-4000-8000-000000000003', '5eed0002-0000-4000-8000-000000000001', now() - interval '40 days', now() - interval '10 days'),
  ('5eed0005-0000-4000-8000-000000000005', 'analiticar-podataka-2204', 'Analitičar podataka',
   'Analiza podataka o korišćenju naših aplikacija i izrada izveštaja za klijente. Opis je još u pripremi.',
   ARRAY['sql', 'python', 'power bi'], 'DATA_AI', 'PERMANENT', 'HYBRID', 'FULL_TIME', 'YEARS_2',
   'Beograd', 'DRAFT', 'CV_DROP', NULL, NULL,
   '5eed0001-0000-4000-8000-000000000003', '5eed0002-0000-4000-8000-000000000001', now() - interval '1 day', now() - interval '1 day'),
  ('5eed0005-0000-4000-8000-000000000006', 'berac-malina-5143', 'Berač malina',
   'Sezonski posao u berbi malina od juna do avgusta. Obezbeđen je prevoz iz Novog Sada, obrok i isplata na kraju svake nedelje.',
   ARRAY['berba', 'sezonski posao', 'poljoprivreda'], 'AGRICULTURE', 'SEASONAL', 'FIELD', 'FULL_TIME', 'NONE',
   'Novi Sad', 'OPEN', 'CONTACT_ONLY', '[{"type":"phone","value":"+381 21 555 0142","description":"radnim danima"}]', NULL,
   '5eed0001-0000-4000-8000-000000000004', '5eed0002-0000-4000-8000-000000000002', now() - interval '4 days', now() - interval '4 days'),
  ('5eed0005-0000-4000-8000-000000000007', 'vozac-dostavnog-vozila-6630', 'Vozač dostavnog vozila',
   'Dostava sveže robe marketima u Novom Sadu i okolini. Potrebna je vozačka dozvola B kategorije i poznavanje grada.',
   ARRAY['vozač', 'b kategorija', 'dostava'], 'TRANSPORT_LOGISTICS', 'FIXED_TERM', 'FIELD', 'FULL_TIME', 'YEARS_1',
   'Novi Sad', 'OPEN', 'CV_DROP', NULL, NULL,
   '5eed0001-0000-4000-8000-000000000002', '5eed0002-0000-4000-8000-000000000002', now() - interval '2 days', now() - interval '2 days'),
  ('5eed0005-0000-4000-8000-000000000008', 'medicinska-sestra-tehnicar-u-pedijatriji-8095', 'Medicinska sestra/tehničar u pedijatriji',
   'Za novu dečju ambulantu tražimo medicinsku sestru ili tehničara sa položenim stručnim ispitom i iskustvom u radu sa decom.',
   ARRAY['pedijatrija', 'medicinska sestra', 'zdravstvo'], 'HEALTHCARE', 'PERMANENT', 'ONSITE', 'FULL_TIME', 'YEARS_1',
   'Čačak', 'OPEN', 'CONTACT_ONLY', '[{"type":"email","value":"posao@klinika-sunce.example"},{"type":"phone","value":"+381 32 555 010"}]', NULL,
   '5eed0001-0000-4000-8000-000000000003', '5eed0002-0000-4000-8000-000000000003', now() - interval '2 days', now() - interval '2 days'),
  ('5eed0005-0000-4000-8000-000000000009', 'recepcioner-3481', 'Recepcioner',
   'Prijem pacijenata, zakazivanje pregleda i vođenje dokumentacije u popodnevnoj smeni. Iskustvo nije neophodno.',
   ARRAY['recepcija', 'administracija'], 'ADMIN_OFFICE', 'PERMANENT', 'ONSITE', 'PART_TIME', 'NONE',
   'Čačak', 'OPEN', 'CV_DROP', NULL, NULL,
   '5eed0001-0000-4000-8000-000000000007', '5eed0002-0000-4000-8000-000000000003', now() - interval '1 day', now() - interval '1 day'),
  ('5eed0005-0000-4000-8000-000000000010', 'ilustracije-za-decju-knjigu-2957', 'Ilustracije za dečju knjigu',
   'Tražim ilustratora za saradnju na slikovnici od dvadeset strana. Stil je akvarel, a rok za skice je mesec dana.',
   ARRAY['ilustracija', 'dečja knjiga', 'akvarel'], 'ARTS_CULTURE', 'FREELANCE', 'REMOTE', 'PART_TIME', 'YEARS_2',
   'Kragujevac', 'OPEN', 'EXTERNAL_URL', NULL, 'https://stefan-ilic.example/saradnja',
   '5eed0001-0000-4000-8000-000000000004', '5eed0001-0000-4000-8000-000000000004', now() - interval '12 hours', now() - interval '12 hours');

INSERT INTO salary (job_id, min, max, currency, period) VALUES
  ('5eed0005-0000-4000-8000-000000000001', 250000, 350000, 'RSD', 'MONTH'),
  ('5eed0005-0000-4000-8000-000000000004', 3000,   4000,   'EUR', 'MONTH'),
  ('5eed0005-0000-4000-8000-000000000006', 450,    NULL,   'RSD', 'HOUR'),
  ('5eed0005-0000-4000-8000-000000000007', 95000,  110000, 'RSD', 'MONTH'),
  ('5eed0005-0000-4000-8000-000000000010', 60000,  NULL,   'RSD', 'JOB');
