set ylabel "1-P(X <= x)"
set terminal emf
set output "intercontacttimes.emf"
plot '10m_radiorange.cdf' title "10m" smooth unique, '30m_radiorange.cdf' title "30m" smooth unique, '50m_radiorange.cdf' title "50m" smooth unique
