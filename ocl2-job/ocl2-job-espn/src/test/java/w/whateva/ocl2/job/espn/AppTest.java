package w.whateva.ocl2.job.espn;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.Test;
import w.whateva.ocl2.api.stats.dto.box.GameBoxScore;
import w.whateva.ocl2.job.integration.dto.MMatchup;
import w.whateva.ocl2.job.mapping.GameMapper;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class AppTest {

    @Test
    public void parseSchedule() throws IOException {

        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

        MMatchup mMatchup = objectMapper.readValue(getClass().getResourceAsStream("/2019.01.json"), MMatchup.class);

        List<GameBoxScore> boxScores = GameMapper.toBoxScores(mMatchup);

        System.out.println(boxScores.size());
    }

    private static final int MAX_START = 7;
    private static final double PI = Math.PI;
    private static final double MASS_COEFFICIENT = 1;
    private static final double BALMER_COEFFICIENT = 364.50682; // nm
    private static final double RYDBERG_COEFFICIENT = 1.097373157 * Math.pow(10, 7) * Math.pow(10, -9);
    private static final double WISHDROP_COEFFICIENT = 1175; // nm // 1169.8984
    private static final double WISHDROP_COEFFICIENT_LYMAN = 146.2373; // nm

    private static final double RYDBERG_COEFFICIENT_W = 32d / 3 / WISHDROP_COEFFICIENT;

    @Test
    public void radial_node() {

        int dimensions = 5;
        double target = 360d / dimensions;
        double epsilon = .02;

        IntStream.range(1, 1000).forEach(i -> {
            IntStream.range(1, 1000).forEach(j -> {
                double n = Math.acos(1 / sqrt(i)) * 180 / PI * j % 360;
                if (n >= target - epsilon && n <= target + epsilon) {
                    System.out.println(i + ", " + j + ": " + print_number(n, 4));
                }
            });
        });
    }

    private double sq_wavelength(int d) {
        return IntStream.range(2, d)
                .mapToDouble(i -> wishdrop_2(i))
                .sum();
    }

    private double wishdrop_2(int i) {
        return Math.pow(1 / sqrt(2), i);
    }

    @Test
    public void physics() {

        System.out.println("w: " + RYDBERG_COEFFICIENT);
        System.out.println("w: " + RYDBERG_COEFFICIENT / RYDBERG_COEFFICIENT_W);
        System.out.println("w: " + WISHDROP_COEFFICIENT_LYMAN * 8);

        IntStream.range(1, 10)
                .forEach(length -> {
                    IntStream.range(1, MAX_START + 13)
                            .forEach(start -> {
                                // System.out.println("" + length + " in " + (start + length - 1));
                                System.out.println(print_particle(start, length));
                            });
                });
    }

    // 146.2, 584.9, 1316.1, 2339.8, 3655.9, 5264.5, 7165.6

    @Test
    public void rydbergTest() {
        IntStream.range(1, 15).forEach(i -> {
                    IntStream.range(1, 15).forEach(j -> {
                        double to = i;
                        double from = j;
                        double balmer = balmer(to, from);
                        double rydberg = rydberg(to, from);
                        double wishdrop = wishdrop(to, from);

                        double outer = Math.max(to, from); // outer shell principle number
                        double inner = Math.min(from, to); // inner shell principle number
                        double pos_neg = to > from ? -1 : 1; // absorption or emission ?
                        double dimensions_orbital = inner + 2; // reference frame number of dimensions
                        double dimensions_photon = outer - inner + 2; // ejected photon number of dimensions

                        if (to < from) {
                            String msg = String.format("%s -> %s:%s rydberg: %s; wishdrop: %s; ratio: %s; %s",
                                    (int)from,
                                    (int) to,
                                    " (" + (int)dimensions_orbital + ".." + (int)dimensions_photon + ", " +
                                            (int)squared(inner) + ".." + (int)squared(outer) + ", " +
                                            print_number((squared(outer) / squared(inner)) - 1) + "/" + (int)squared(outer) + ")",
                                    print_number(rydberg),
                                    print_number(wishdrop),
                                    print_number(wishdrop / rydberg, 2),
                                    (Math.abs((wishdrop / rydberg) - 1) < 0.1 ? "*" : ""));
                            System.out.println(msg);
                        }
                    });
                }
        );
    }

    @Test
    public void em() {

        int count = 100000;
        System.out.println("EM magnitude: " + print_number(em_magnitude(count), 3) + "; EM trace: " + print_number(em_spiral_trace(count), 3));
    }

    @Test
    public void strong() {

        int count = 100000;
        System.out.println("strong magnitude: " + print_number(strong_magnitude(count), 3) + "; strong trace: " + print_number(strong_spiral_trace(count), 3));
    }

    private double balmer(double interior, double exterior) {
        if (interior == exterior) return Double.POSITIVE_INFINITY;
        return BALMER_COEFFICIENT * (squared(exterior) / (squared(exterior) - squared(interior)));
    }

    // {\displaystyle {\frac {1}{\lambda }}=RZ^{2}\left({\frac {1}{n_{1}^{2}}}-{\frac {1}{n_{2}^{2}}}\right),}
    private double rydberg(double n_1, double n_2) {
        return rydberg(n_1, n_2, 1);
    }

    // the inverse of (inverse square of inner minus inverse square of outer)
    private double rydberg(double to, double from, int atomicNumber) {
        double outer = Math.max(to, from); // outer shell principle number
        double inner = Math.min(from, to); // inner shell principle number
        double pos_neg = to > from ? -1 : 1; // absorption or emission ?
        return pos_neg /
                (
                        RYDBERG_COEFFICIENT *
                        squared(atomicNumber) *
                    ((1 / squared(inner)) - (0 / squared(outer))));
    }

    // 1169.9 / sqrt(x), 1 / (0.009117598789182882 * (1/4 - 1/(x^2))), 364.50682 * ((x^2) / (x^2 - 4))

    // 146.2373 / sqrt(x + 2 - 3) * sqrt(2 + 3) * 3^2, 1 / (0.009117598789182882 * (1/9 - 1/(x^2)))

    // wishdrop ratio of photon dimensions * inverse wishdrop ratio of orbital dimensions * offset squared
    private double wishdrop(double to, double from) {
        double outer = Math.max(to, from); // outer shell principle number
        double inner = Math.min(from, to); // inner shell principle number
        double pos_neg = to > from ? -1 : 1; // absorption or emission ?
        double dimensions_orbital = inner + 2; // reference frame number of dimensions
        double dimensions_photon = outer - inner + 2; // ejected photon number of dimensions
        return pos_neg *
                WISHDROP_COEFFICIENT / 8 * // rydberg wavelength for base transition (2 -> 1)
                wishdrop_ratio(dimensions_photon) * // wishdrop ratio of photon dimensions
                wishdrop_ratio_inverse(dimensions_orbital) * // inverse wishdrop ratio of reference frame dimensions
                squared(inner); // square the offset dimensions
    }

    // when dimensions are equal (inner is 1/2 outer), the formulas are equivalent
    // in the wishdrop case, it's WISHDROP_COEFFICIENT_LYMAN * inner^2
    // in the rydberg case, it's RYDBERG_COEFFICIENT * (1/inner^2 - 1/outer^2), which is...
    // 1 / RYDBERG_COEFFICIENT / 3 * outer^2... or i suppose 1 / RYDBERG_COEFFICIENT / 3 * 4 * inner^2

    private double wishdrop_normalization(double n) {
        double prongs = n - 1;
        // return prongs * Math.sqrt(prongs);
        // return Math.sqrt(dimensions + 2) * 2 / Math.pow(4 / dimensions, 2);
        // return Math.sqrt(dimensions + 2) * Math.pow(dimensions, 2);
        // return Math.sqrt(n + 2) * surface_area((int)n) * n / 2 / PI;
        return sqrt(n + 2) * squared(n); // * surface_area((int)n) * n / 2 / PI;
    }

    // when going up energy level(s), consider one less dimension, and multiply the number of "prongs"
    // by the long projection... for example: 2 * sqrt(2), 3 * sqrt(3), 4 * sqrt(4), and so on
    private double wishdrop_absorption(double dimensions) {
        if (dimensions <= 1) return 1;
        double offset = dimensions - 1;
        return sqrt(offset);
    }

    private double wishdrop_ratio(double dimensions) {
        if (dimensions == 0) return Double.POSITIVE_INFINITY;
        return 1 / sqrt(dimensions);
    }

    private double wishdrop_ratio_inverse(double dimensions) {
        return Math.sqrt(dimensions);
    }

    private static double sqrt(double number) {
        return Math.sqrt(number);
    }

    private static double squared(double number) {
        return Math.pow(number, 2);
    }

    private double mass(int interior, int exterior) {
        if (interior <= 3) return 0;
        int offset = offset(interior, exterior);
        boolean isInteriorEven = interior % 2 == 0;
        boolean isOffsetEven = offset % 2 == 0;
        if (isInteriorEven && !isOffsetEven) {
            offset = offset + 1;
            interior = interior + 1;
            exterior = exterior + 1;
        }
        return MASS_COEFFICIENT * (pythagorean_offset(surface_area(interior), surface_area(interior), offset));
    }

    private int offset(int interior, int exterior) {
        return exterior - interior;
    }

    private double surface_area(int dimensions) {
        return dimensions % 2 == 0 ? surface_area_even(dimensions) : surface_area_odd(dimensions);
    }

    private double surface_area_odd(int dimensions) {
        if (dimensions == 1) {
            return 2 * PI;
        } else {
            return surface_area_odd(dimensions - 2) * (PI / ((dimensions - 1d) / 2));
        }
    }

    private double surface_area_even(int dimensions) {
        if (dimensions == 0) {
            return 2;
        } else {
            return surface_area_even(dimensions - 2) * 2 * PI / (dimensions - 1);
        }
    }

    private double pythagorean_offset(double a, double b, int dimensions) {
        if (dimensions == 0) {
            return 1;
        }
        if (dimensions == 1) {
            return pythagorean_c(a, b);
        } else {
            return pythagorean_offset(a, pythagorean_c(a, b), dimensions - 1);
        }
    }

    private double pythagorean_c(double a, double b) {
        return sqrt(squared(a) + squared(b));
    }

    private double pythagorean_a(double a, double c) {
        return sqrt(squared(c) - squared(a));
    }

    private double em_spiral_trace(int count) {
        return IntStream.range(0, count).mapToDouble(this::em_magnitude).sum() + em_magnitude(count);
    }

    private double em_magnitude(int count) {
        return Math.pow(1 / sqrt(2), count - 1);
    }

    private double strong_spiral_trace(int count) {
        return IntStream.range(0, count).mapToDouble(this::strong_magnitude).sum() + strong_magnitude(count);
    }

    private double strong_magnitude(int count) {
        return Math.pow(1d / 2, count - 1);
    }

    private String print_particle(int start, int length) {
        int end = start + length - 1;
        double mass = balmer(length, end);
        double wishdrop = wishdrop(length, end);
        return IntStream.range(start, end + 1)
                .mapToObj(Integer::toString)
                .collect(Collectors.joining(", ")) + ": " + print_number(mass) + " / " + print_number(wishdrop) + " / " + particle(length, end);
    }

    private String print_number(double number) {
        return print_number(number, 1);
    }

    private String print_number(double number, int scale) {
        if (number == Double.POSITIVE_INFINITY) return "INF";
        if (Double.isNaN(number)) return "";
        return BigDecimal.valueOf(number).setScale(scale, RoundingMode.HALF_UP).toString();
    }

    private String particle(int interior, int exterior) {
        switch (interior) {
            case 1: return "graviton";
            case 2:
                switch (exterior) {
                    case 2: return "photon (infrared)";
                    case 3: return "photon (red)";
                    case 4: return "photon (yellow)";
                    case 5: return "photon (green)";
                    case 6: return "photon (cyan)";
                    case 7: return "photon (blue)";
                    case 8: return "photon (violet)";
                    case 9: return "photon (ultraviolet)";
                    default: return "photon";
                }
            case 3:
                switch (exterior) {
                    case 3: return "gluon (colorless)";
                    case 4: return "gluon (red)";
                    case 5: return "gluon (yellow)";
                    case 6: return "gluon (green)";
                    case 7: return "gluon (cyan)";
                    case 8: return "gluon (blue)";
                    case 9: return "gluon (violet)";
                    default: return "photon";
                }
            case 4: {
                switch (exterior) {
                    case 4:
                    case 5:
                        return "down 1-quark (1)";
                    case 6:
                    case 7:
                        return "strange 1-quark (20.4)";
                    case 8:
                    case 9:
                        return "bottom 1-quark (44x = 889)";
                    default: return "1-quark";
                }
            }
            case 5: {
                switch (exterior) {
                    case 7:
                        return "Z boson (19000)";
                    default: return "W boson (17000)";
                }
            }
            case 6: {
                switch (exterior) {
                    case 6:
                    case 7:
                        return "up 2-quark (.47)";
                    case 8:
                    case 9:
                        return "charm 2-quark (582x = 272)";
                    case 10:
                    case 11:
                        return "top 2-quark (135x = 37000)";
                    default: return "2-quark";
                }
            }
            case 7: {
                return "higgs";
            }
            case 8: {
                switch (exterior) {
                    case 8: return "electron (.11)";
                    case 9: return "positron (.11)";
                    case 10: return "muon (22.3)";
                    case 11: return "antimuon (22.3)";
                    case 12: return "tau (377)";
                    case 13: return "antitau (377)";
                    default: return "NONE (e/p)";
                }
            }
            case 9: {
                switch (exterior) {
                    case 9: return "e neutrino";
                    case 10: return "p neutrino";
                    case 11: return "muon neutrino (.04)";
                    case 12: return "muon antineutrino (.04)";
                    case 13: return "tau neutrino (3.8)";
                    case 14: return "tau antineutrino (3.8)";
                    default: return "neutrino";
                }
            }
            default: return "NONE";
        }
    }
}
