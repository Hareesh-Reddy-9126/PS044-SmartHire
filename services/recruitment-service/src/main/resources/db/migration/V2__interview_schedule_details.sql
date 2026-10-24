ALTER TABLE recruitments
    ADD COLUMN interview_date DATE,
    ADD COLUMN interview_start_time TIME,
    ADD COLUMN interviewer VARCHAR(200),
    ADD COLUMN interview_location VARCHAR(300);