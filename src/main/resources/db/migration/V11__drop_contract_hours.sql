-- Drop the dead contract_hours column. The contract-tier model it encoded (a fixed
-- 37h/40h split) was a misread of the real schedule — everyone is 40h and everyone
-- rotates weekends — so the engine no longer reads it (folga gating removed, decision
-- 016). Column and its UI selector removed. See decisions/021.
ALTER TABLE employee DROP COLUMN contract_hours;
