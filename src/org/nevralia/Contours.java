/**
 * Copyright (c) 2012 Jun Mei
 * 
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * 
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 * 
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package org.nevralia;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ForkJoinPool;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.GnuParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;

public class Contours {

    private static final String OPT_INPUT_SHORT = "i";
    private static final String OPT_INPUT_LONG = "input";
    private static final String OPT_CANNY_SHORT = "t";
    private static final String OPT_CANNY_LONG = "thresholds";

    /**
     * @param args
     * @throws ParseException
     * @throws IOException
     */
    public static void main(String[] args) throws ParseException, IOException {
        Options opts = initOptions();

        if (args.length == 0) {
            printUsage(opts);
            final int missingArguments = 1;
            System.exit(missingArguments);
        }

        CommandLineParser parser = new GnuParser();
        CommandLine cl = parser.parse(opts, args);
        List<File> images = initImages(cl);
        Collections.shuffle(images);

        Collection<Double> thresholds = initThresholds(cl);
        double t1 = Collections.min(thresholds);
        double t2 = Collections.max(thresholds);

        ForkJoinPool pool = new ForkJoinPool();
        ContourProcessor action = new ContourProcessor(images, 0,
            images.size(), t1, t2);
        pool.invoke(action);
    }

    private static Options initOptions() {
        Options results = new Options();

        Option input = new Option(OPT_INPUT_SHORT, OPT_INPUT_LONG, true,
            "List of image files");
        input.setArgs(Option.UNLIMITED_VALUES);
        results.addOption(input);

        Option cannyThreshold = new Option(OPT_CANNY_SHORT, OPT_CANNY_LONG,
            true, "Canny filter thresholds (up to 2 numbers)");
        input.setArgs(2);
        results.addOption(cannyThreshold);

        return results;
    }

    /**
     * Returns a collection of Canny filter thresholds parsed from the command
     * line
     * 
     * @param cl
     *            List of parsed arguments
     * @return
     */
    private static Collection<Double> initThresholds(CommandLine cl) {
        String[] tokens = null;

        if (cl.hasOption(OPT_CANNY_SHORT)) {
            tokens = cl.getOptionValues(OPT_CANNY_SHORT);
        } else if (cl.hasOption(OPT_CANNY_LONG)) {
            tokens = cl.getOptionValues(OPT_CANNY_LONG);
        }

        ArrayList<Double> result = new ArrayList<>();

        if (tokens != null) {
            for (String token : tokens) {
                double threshold = Double.parseDouble(token);
                result.add(threshold);
            }
        }

        return (result.size() > 1) ? result : Arrays.asList(5d, 50d);
    }

    private static void printUsage(Options opts) {
        HelpFormatter help = new HelpFormatter();
        help.printHelp("rmbg -t [floats] -i [list of files]", opts);
    }

    private static List<File> initImages(CommandLine cl) throws IOException {
        String[] tokens = null;

        if (cl.hasOption(OPT_INPUT_SHORT)) {
            tokens = cl.getOptionValues(OPT_INPUT_SHORT);
        } else if (cl.hasOption(OPT_INPUT_LONG)) {
            tokens = cl.getOptionValues(OPT_INPUT_LONG);
        }

        List<File> result = new ArrayList<>();

        if (tokens != null && tokens.length > 0) {
            Set<String> uniquePaths = new HashSet<>();

            for (String token : tokens) {
                File file = new File(token);

                if (file.exists() == false || file.isFile() == false) {
                    continue;
                }

                String path = file.getCanonicalPath();

                if (uniquePaths.contains(path) == false) {
                    uniquePaths.add(path);
                    result.add(file);
                }
            }
        }

        return result;
    }
}