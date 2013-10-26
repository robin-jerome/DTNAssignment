set ylabel "P(X <= x)"
set terminal emf
set output "temp.plot.emf"
plot 'one/reports/intercontact_times_10_ContactTimesReport.cdf' title "one/reports/intercontact" smooth unique
