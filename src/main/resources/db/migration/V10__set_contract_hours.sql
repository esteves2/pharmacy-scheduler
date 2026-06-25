-- 37h employees: Paula(1), Jéssica(3), Cristina(5), Carolina(7), Paulina(9)
-- 40h employees: Nídia(2), Andreia(4), Natty(6), Crisanta(8), Sara(10) — already default 40
UPDATE employee SET contract_hours = 37 WHERE id IN (1, 3, 5, 7, 9);
