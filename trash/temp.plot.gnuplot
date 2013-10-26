set ylabel "P(X <= x)"
set terminal emf
set output "temp.plot.emf"
plot 'one/reports/intercontact_times_10_InterContactTimesReport.cdf' title "one/reports/intercontact" smooth unique, 'one/reports/intercontact_times_30_InterContactTimesReport.cdf' title "one/reports/intercontact" smooth unique, 'one/reports/intercontact_times_50_InterContactTimesReport.cdf' title "one/reports/intercontact" smooth unique
