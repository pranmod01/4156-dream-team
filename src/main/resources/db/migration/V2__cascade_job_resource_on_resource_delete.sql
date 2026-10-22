-- AI-generated (Claude, Claude Opus 5.5), reviewed by Rob
-- Deleting a resource now also removes its job_resource rows, so a
-- resource used by finished jobs can be deleted. The service still
-- refuses to delete a resource held by a running job.
--
-- V1 left the foreign key unnamed, and H2 (tests) and PostgreSQL give it
-- different default names, so the table is rebuilt instead of altering
-- the constraint by name. Existing assignments are copied across, and the
-- new constraints are named so later migrations can refer to them.

CREATE TABLE job_resource_new (
    job_id       BIGINT NOT NULL,
    resource_id  BIGINT NOT NULL,
    CONSTRAINT pk_job_resource PRIMARY KEY (job_id, resource_id),
    CONSTRAINT fk_job_resource_job FOREIGN KEY (job_id)
        REFERENCES job(id) ON DELETE CASCADE,
    CONSTRAINT fk_job_resource_resource FOREIGN KEY (resource_id)
        REFERENCES resource(id) ON DELETE CASCADE
);

INSERT INTO job_resource_new (job_id, resource_id)
SELECT job_id, resource_id FROM job_resource;

DROP TABLE job_resource;

ALTER TABLE job_resource_new RENAME TO job_resource;

CREATE INDEX idx_job_resource_resource ON job_resource (resource_id);
