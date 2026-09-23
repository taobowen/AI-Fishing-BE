ALTER TABLE lakes ADD COLUMN card_image_path VARCHAR(255);

UPDATE lakes SET card_image_path = 'lakes/head.jpg' WHERE id = '44444444-4444-4444-4444-444444444444';
UPDATE lakes SET card_image_path = 'lakes/rice.jpg' WHERE id = '44444444-4444-4444-4444-444444444445';
UPDATE lakes SET card_image_path = 'lakes/scugog.jpg' WHERE id = '44444444-4444-4444-4444-444444444446';
UPDATE lakes SET card_image_path = 'lakes/simcoe.jpg' WHERE id = '44444444-4444-4444-4444-444444444447';
