package com.codingame.gameengine.runner;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.Charset;
import java.nio.file.Paths;
import java.util.List;
import java.util.Properties;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Options;

import com.codingame.gameengine.runner.dto.GameResult;
import com.google.common.io.Files;

public class CommandLineInterface {

    public static void main(String[] args) {
        try {
            Options options = new Options();

            // Define required options
            options.addOption("h", false, "Print the help")
                    .addOption("p1", true, "Required. Player 1 command line.")
                    .addOption("p2", true, "Required. Player 2 command line.")
                    .addOption("n1", true, "Player 1 name. Default: Player1")
                    .addOption("n2", true, "Player 2 name. Default: Player2")
                    .addOption("a1", true, "Player 1 avatar url. Default: null")
                    .addOption("a2", true, "Player 2 avatar url. Default: null")
                    .addOption("s", false, "Server mode")
                    .addOption("l", true, "File output for logs")
                    .addOption("d", true, "Referee initial data");

            CommandLine cmd = new DefaultParser().parse(options, args);

            if (cmd.hasOption("h") || !cmd.hasOption("p1") || !cmd.hasOption("p2")) {
                new HelpFormatter().printHelp( "-p1 <player1 command line> -p2 <player2 command line> -l <log_folder>", options);
                System.exit(0);
            }

            // Launch Game
            MultiplayerGameRunner gameRunner = new MultiplayerGameRunner();

            //Choose league level
            gameRunner.setLeagueLevel(3);

            //Add players
            Method addAgent = MultiplayerGameRunner.class.getDeclaredMethod("addAgent", Agent.class, String.class, String.class);
            addAgent.setAccessible(true);
            
            String cmd1 = cmd.getOptionValue("p1");
            String name1 = cmd.getOptionValue("n1", "Player1");
            String avatar1 = cmd.getOptionValue("a1", null);
            addAgent.invoke(gameRunner, new CommandLinePlayerAgentModified(cmd1), name1, avatar1);
            
            String cmd2 = cmd.getOptionValue("p2");
            String name2 = cmd.getOptionValue("n2", "Player2");
            String avatar2 = cmd.getOptionValue("a2", null);
            addAgent.invoke(gameRunner, new CommandLinePlayerAgentModified(cmd2), name2, avatar2);

            if (cmd.hasOption("d")) {
                String[] parse = cmd.getOptionValue("d").split("=", 0);
                Long seed = Long.parseLong(parse[1]);
                gameRunner.setSeed(seed);
            } else {
                gameRunner.setSeed(System.currentTimeMillis());
            }

            GameResult result = gameRunner.gameResult;

            if (cmd.hasOption("s")) {
                gameRunner.start();
            }

            Method initialize = GameRunner.class.getDeclaredMethod("initialize", Properties.class);
            initialize.setAccessible(true);
            initialize.invoke(gameRunner, new Properties());

            Method runAgents = GameRunner.class.getDeclaredMethod("runAgents");
            runAgents.setAccessible(true);
            runAgents.invoke(gameRunner);

            if (cmd.hasOption("l")) {
                Method getJSONResult = GameRunner.class.getDeclaredMethod("getJSONResult");
                getJSONResult.setAccessible(true);

                Files.asCharSink(Paths.get(cmd.getOptionValue("l")).toFile(), Charset.defaultCharset())
                        .write((String) getJSONResult.invoke(gameRunner));
            }

            for (int i = 0; i < 2; ++i) {
                System.out.println(result.scores.get(i));
            }

            for (String line : result.uinput) {
                System.out.println(line);
            }

            // We have to clean players process properly
            Field getPlayers = GameRunner.class.getDeclaredField("players");
            getPlayers.setAccessible(true);
            @SuppressWarnings("unchecked")
            List<Agent> players = (List<Agent>) getPlayers.get(gameRunner);

            if (players != null) {
                for (Agent player : players) {
                    Field getProcess = CommandLinePlayerAgent.class.getDeclaredField("process");
                    getProcess.setAccessible(true);
                    Process process = (Process) getProcess.get(player);

                    process.destroy();
                }
            }
        } catch (Exception e) {
            System.err.println(e);
            e.printStackTrace(System.err);
            System.exit(1);
        }
    }

} 