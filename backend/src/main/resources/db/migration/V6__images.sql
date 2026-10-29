-- the file name in app.images.dir (<uuid>.jpg), or NULL for no image
ALTER TABLE player ADD COLUMN image VARCHAR(40) NULL;
ALTER TABLE team ADD COLUMN image VARCHAR(40) NULL;
ALTER TABLE competition ADD COLUMN image VARCHAR(40) NULL;
