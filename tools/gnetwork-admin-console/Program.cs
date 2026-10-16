using System;
using System.Collections.Generic;
using System.Diagnostics;
using System.IO;
using System.Reflection;
using System.Runtime.InteropServices;
using System.Text;
using System.Text.RegularExpressions;

[assembly: AssemblyTitle("G'Network Admin Console")]
[assembly: AssemblyDescription("Private Railway-backed moderator account manager for G'Network")]
[assembly: AssemblyCompany("G'Network")]
[assembly: AssemblyProduct("G'Network Admin Console")]
[assembly: AssemblyCopyright("Copyright 2026")]
[assembly: AssemblyVersion("1.0.1.0")]
[assembly: AssemblyFileVersion("1.0.1.0")]

internal static class Program
{
    private const string AppName = "G'Network Admin Console";
    private const string Version = "1.0.1";
    private static readonly string ConfigDirectory = Path.Combine(
        Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData),
        "GNetworkAdminConsole");
    private static readonly string TargetFile = Path.Combine(ConfigDirectory, "railway-target.txt");
    private static readonly Regex SafeTargetArgument = new Regex(
        @"^[A-Za-z0-9._:/@\\=+\-]+$",
        RegexOptions.CultureInvariant);

    [DllImport("shell32.dll", SetLastError = true)]
    private static extern IntPtr CommandLineToArgvW(
        [MarshalAs(UnmanagedType.LPWStr)] string commandLine,
        out int argumentCount);

    [DllImport("kernel32.dll")]
    private static extern IntPtr LocalFree(IntPtr memory);

    public static int Main(string[] args)
    {
        Console.OutputEncoding = Encoding.UTF8;
        Console.Title = AppName;

        if (args.Length > 0 && string.Equals(args[0], "--self-test", StringComparison.OrdinalIgnoreCase))
        {
            return SelfTest();
        }
        if (args.Length > 0 && string.Equals(args[0], "--version", StringComparison.OrdinalIgnoreCase))
        {
            Console.WriteLine(Version);
            return 0;
        }

        try
        {
            return RunMenu();
        }
        catch (Exception exception)
        {
            WriteError("Unexpected error: " + exception.Message);
            Pause();
            return 1;
        }
    }

    private static int RunMenu()
    {
        while (true)
        {
            DrawHeader();
            string railway = FindRailway();
            bool configured = LoadTargetArguments() != null;

            Console.WriteLine("Railway CLI : " + (railway == null ? "Not installed" : "Ready"));
            Console.WriteLine("Backend     : " + (configured ? "Configured" : "Not configured"));
            Console.WriteLine();
            Console.WriteLine("1. First-time setup / change Railway backend");
            Console.WriteLine("2. Create or reset MODERATOR account");
            Console.WriteLine("3. Create or reset OWNER account");
            Console.WriteLine("4. Revoke admin account");
            Console.WriteLine("5. Open private backend shell");
            Console.WriteLine("6. Railway login / account status");
            Console.WriteLine("7. Forget saved backend target");
            Console.WriteLine("0. Exit");
            Console.WriteLine();
            Console.Write("Choose: ");

            string choice = (Console.ReadLine() ?? "").Trim();
            Console.WriteLine();

            switch (choice)
            {
                case "1":
                    Setup();
                    break;
                case "2":
                    Grant("MODERATOR");
                    break;
                case "3":
                    Grant("OWNER");
                    break;
                case "4":
                    Revoke();
                    break;
                case "5":
                    OpenShell();
                    break;
                case "6":
                    RailwayLogin();
                    break;
                case "7":
                    ForgetTarget();
                    break;
                case "0":
                    return 0;
                default:
                    WriteWarning("Choose one of the numbered options.");
                    Pause();
                    break;
            }
        }
    }

    private static void DrawHeader()
    {
        try
        {
            Console.Clear();
        }
        catch (IOException)
        {
            Console.WriteLine();
        }
        Console.ForegroundColor = ConsoleColor.Yellow;
        Console.WriteLine("============================================================");
        Console.WriteLine("  G'NETWORK ADMIN CONSOLE                                  ");
        Console.WriteLine("============================================================");
        Console.ResetColor();
        Console.WriteLine("Passwords and player UUIDs are never saved by this program.");
        Console.WriteLine();
    }

    private static void Setup()
    {
        DrawHeader();
        if (!EnsureRailwayInstalled())
        {
            Pause();
            return;
        }

        Console.WriteLine("Railway will open a browser so you can authenticate.");
        Console.WriteLine();
        int loginExit = RunRailway(new List<string> { "login" });
        if (loginExit != 0)
        {
            WriteError("Railway login did not complete.");
            Pause();
            return;
        }

        Console.WriteLine();
        Console.WriteLine("In the Railway dashboard:");
        Console.WriteLine("  1. Open the G'Network project and Production environment.");
        Console.WriteLine("  2. Right-click the BACKEND service, not PostgreSQL.");
        Console.WriteLine("  3. Choose \"Copy SSH Command\".");
        Console.WriteLine("  4. Paste that complete command below.");
        Console.WriteLine();
        Console.Write("Railway SSH command: ");
        string pasted = (Console.ReadLine() ?? "").Trim();

        List<string> target;
        string error;
        if (!TryExtractTargetArguments(pasted, out target, out error))
        {
            WriteError(error);
            Pause();
            return;
        }

        Directory.CreateDirectory(ConfigDirectory);
        File.WriteAllLines(TargetFile, target.ToArray(), new UTF8Encoding(false));
        WriteSuccess("Backend target saved locally. No credential was stored.");
        Console.WriteLine();
        Console.WriteLine("Testing the authenticated Railway account:");
        int whoamiExit = RunRailway(new List<string> { "whoami" });
        if (whoamiExit != 0)
        {
            WriteWarning("The target was saved, but Railway account verification failed.");
        }
        Pause();
    }

    private static void Grant(string role)
    {
        List<string> target;
        if (!RequireReadyTarget(out target))
        {
            Pause();
            return;
        }

        DrawHeader();
        Console.WriteLine("Create or reset a " + role + " account");
        Console.WriteLine();
        Console.WriteLine("The player must have opened K > Friends & Chat at least once.");
        Console.WriteLine("Running this again resets their initial password, revokes");
        Console.WriteLine("their other moderator sessions, and forces a password change.");
        Console.WriteLine();

        string uuid = ReadUuid();
        if (uuid == null)
        {
            WriteWarning("Cancelled.");
            Pause();
            return;
        }

        if (!Confirm("Continue with " + role + " access for this UUID?"))
        {
            WriteWarning("Cancelled.");
            Pause();
            return;
        }

        Console.WriteLine();
        Console.WriteLine("The remote backend will now request an initial password.");
        Console.WriteLine("Your typing will be hidden and this program will not receive it.");
        Console.WriteLine();

        List<string> remote = new List<string>();
        remote.Add("env");
        remote.Add("ADMIN_TARGET_UUID=" + uuid);
        remote.Add("ADMIN_ROLE=" + role);
        remote.Add("node");
        remote.Add("/app/dist/admin-cli.js");

        int exitCode = RunRemote(target, remote);
        Console.WriteLine();
        if (exitCode == 0)
        {
            WriteSuccess(role + " account operation completed.");
        }
        else
        {
            WriteError("The backend command failed with exit code " + exitCode + ".");
        }
        Pause();
    }

    private static void Revoke()
    {
        List<string> target;
        if (!RequireReadyTarget(out target))
        {
            Pause();
            return;
        }

        DrawHeader();
        Console.WriteLine("Revoke an admin account");
        Console.WriteLine();
        string uuid = ReadUuid();
        if (uuid == null)
        {
            WriteWarning("Cancelled.");
            Pause();
            return;
        }

        WriteWarning("This disables the admin account and deletes its active admin sessions.");
        if (!Confirm("Revoke access for this UUID?"))
        {
            WriteWarning("Cancelled.");
            Pause();
            return;
        }

        List<string> remote = new List<string>();
        remote.Add("env");
        remote.Add("ADMIN_ACTION=revoke");
        remote.Add("ADMIN_TARGET_UUID=" + uuid);
        remote.Add("node");
        remote.Add("/app/dist/admin-cli.js");

        int exitCode = RunRemote(target, remote);
        Console.WriteLine();
        if (exitCode == 0)
        {
            WriteSuccess("Admin access revoked.");
        }
        else
        {
            WriteError("The backend command failed with exit code " + exitCode + ".");
        }
        Pause();
    }

    private static void OpenShell()
    {
        List<string> target;
        if (!RequireReadyTarget(out target))
        {
            Pause();
            return;
        }

        DrawHeader();
        Console.WriteLine("Opening the authenticated G'Network backend shell.");
        Console.WriteLine("Type exit to return to this console.");
        Console.WriteLine();

        List<string> arguments = new List<string>();
        arguments.Add("ssh");
        arguments.AddRange(target);
        RunRailway(arguments);
        Pause();
    }

    private static void RailwayLogin()
    {
        DrawHeader();
        if (!EnsureRailwayInstalled())
        {
            Pause();
            return;
        }

        Console.WriteLine("Current Railway account:");
        int status = RunRailway(new List<string> { "whoami" });
        if (status != 0 || Confirm("Log in or switch Railway account?"))
        {
            RunRailway(new List<string> { "login" });
        }
        Pause();
    }

    private static void ForgetTarget()
    {
        DrawHeader();
        if (!File.Exists(TargetFile))
        {
            WriteWarning("No backend target is saved.");
            Pause();
            return;
        }
        if (!Confirm("Forget the saved Railway backend target?"))
        {
            WriteWarning("Cancelled.");
            Pause();
            return;
        }
        File.Delete(TargetFile);
        WriteSuccess("Saved backend target removed. Railway login was not changed.");
        Pause();
    }

    private static bool RequireReadyTarget(out List<string> target)
    {
        target = null;
        if (!EnsureRailwayInstalled())
        {
            return false;
        }
        target = LoadTargetArguments();
        if (target == null)
        {
            WriteError("No backend target is configured. Run option 1 first.");
            return false;
        }
        return true;
    }

    private static bool EnsureRailwayInstalled()
    {
        if (FindRailway() != null)
        {
            return true;
        }

        WriteWarning("Railway CLI is not installed.");
        Console.WriteLine("This computer can install Node.js LTS with winget, then Railway CLI with npm.");
        Console.WriteLine("Nothing will be installed unless you confirm.");
        Console.WriteLine();
        if (!Confirm("Install Node.js LTS and Railway CLI now?"))
        {
            return false;
        }

        int nodeExit = RunProcess(
            "winget.exe",
            new List<string>
            {
                "install",
                "--id", "OpenJS.NodeJS.LTS",
                "--exact",
                "--accept-package-agreements",
                "--accept-source-agreements"
            });
        if (nodeExit != 0)
        {
            WriteError("Node.js installation failed with exit code " + nodeExit + ".");
            return false;
        }

        string npm = FindNpm();
        if (npm == null)
        {
            WriteError("Node.js installed, but npm could not be located. Restart Windows and run this console again.");
            return false;
        }

        int railwayExit = RunProcess(npm, new List<string> { "install", "-g", "@railway/cli" });
        if (railwayExit != 0 || FindRailway() == null)
        {
            WriteError("Railway CLI installation did not complete. Restart Windows and try option 1 again.");
            return false;
        }

        WriteSuccess("Railway CLI installed.");
        return true;
    }

    private static int RunRemote(List<string> target, List<string> remoteArguments)
    {
        List<string> arguments = new List<string>();
        arguments.Add("ssh");
        arguments.AddRange(target);
        arguments.Add("--");
        arguments.AddRange(remoteArguments);
        return RunRailway(arguments);
    }

    private static int RunRailway(List<string> arguments)
    {
        string railway = FindRailway();
        if (railway == null)
        {
            WriteError("Railway CLI is not installed.");
            return 9009;
        }
        return RunProcess(railway, arguments);
    }

    private static int RunProcess(string executable, List<string> arguments)
    {
        ProcessStartInfo start = new ProcessStartInfo();
        bool commandScript = executable.EndsWith(".cmd", StringComparison.OrdinalIgnoreCase)
            || executable.EndsWith(".bat", StringComparison.OrdinalIgnoreCase);

        if (commandScript)
        {
            start.FileName = Environment.GetEnvironmentVariable("ComSpec") ?? "cmd.exe";
            StringBuilder command = new StringBuilder();
            command.Append(QuoteWindowsArgument(executable));
            for (int index = 0; index < arguments.Count; index++)
            {
                command.Append(' ');
                command.Append(QuoteWindowsArgument(arguments[index]));
            }
            start.Arguments = "/d /s /c " + QuoteForCmd(command.ToString());
        }
        else
        {
            start.FileName = executable;
            start.Arguments = JoinArguments(arguments);
        }

        start.UseShellExecute = false;
        start.CreateNoWindow = false;
        Process process = Process.Start(start);
        if (process == null)
        {
            throw new InvalidOperationException("Could not start " + executable + ".");
        }
        process.WaitForExit();
        int exitCode = process.ExitCode;
        process.Dispose();
        return exitCode;
    }

    private static string FindRailway()
    {
        string located = FindOnPath("railway.exe");
        if (located != null) return located;
        located = FindOnPath("railway.cmd");
        if (located != null) return located;

        string appData = Environment.GetFolderPath(Environment.SpecialFolder.ApplicationData);
        string[] candidates =
        {
            Path.Combine(appData, "npm", "railway.cmd"),
            Path.Combine(appData, "npm", "railway.exe"),
            Path.Combine(
                Environment.GetFolderPath(Environment.SpecialFolder.UserProfile),
                ".railway", "bin", "railway.exe")
        };
        for (int index = 0; index < candidates.Length; index++)
        {
            if (File.Exists(candidates[index])) return candidates[index];
        }
        return null;
    }

    private static string FindNpm()
    {
        string located = FindOnPath("npm.cmd");
        if (located != null) return located;
        string programFiles = Environment.GetFolderPath(Environment.SpecialFolder.ProgramFiles);
        string candidate = Path.Combine(programFiles, "nodejs", "npm.cmd");
        return File.Exists(candidate) ? candidate : null;
    }

    private static string FindOnPath(string name)
    {
        string path = Environment.GetEnvironmentVariable("PATH") ?? "";
        string[] directories = path.Split(Path.PathSeparator);
        for (int index = 0; index < directories.Length; index++)
        {
            string directory = directories[index].Trim().Trim('"');
            if (directory.Length == 0) continue;
            try
            {
                string candidate = Path.Combine(directory, name);
                if (File.Exists(candidate)) return candidate;
            }
            catch
            {
            }
        }
        return null;
    }

    private static bool TryExtractTargetArguments(
        string command,
        out List<string> target,
        out string error)
    {
        target = null;
        error = null;
        if (string.IsNullOrWhiteSpace(command))
        {
            error = "Paste the complete SSH command copied from Railway.";
            return false;
        }

        string[] tokens;
        try
        {
            tokens = SplitCommandLine(command);
        }
        catch (Exception exception)
        {
            error = "The pasted command could not be read: " + exception.Message;
            return false;
        }

        if (tokens.Length < 3)
        {
            error = "The copied command is incomplete.";
            return false;
        }

        string executableName = Path.GetFileName(tokens[0]);
        if (!string.Equals(executableName, "railway", StringComparison.OrdinalIgnoreCase)
            && !string.Equals(executableName, "railway.exe", StringComparison.OrdinalIgnoreCase)
            && !string.Equals(executableName, "railway.cmd", StringComparison.OrdinalIgnoreCase))
        {
            error = "For safety, only a command beginning with \"railway ssh\" is accepted.";
            return false;
        }
        if (!string.Equals(tokens[1], "ssh", StringComparison.OrdinalIgnoreCase))
        {
            error = "The copied command must be a Railway SSH command.";
            return false;
        }

        List<string> parsed = new List<string>();
        for (int index = 2; index < tokens.Length; index++)
        {
            string token = tokens[index];
            if (token == "--")
            {
                error = "Copy the service SSH command without an appended remote command.";
                return false;
            }
            if (token.Length == 0 || token.Length > 300 || !SafeTargetArgument.IsMatch(token))
            {
                error = "The SSH command contains an unexpected argument. Copy it again directly from Railway.";
                return false;
            }
            parsed.Add(token);
        }

        if (parsed.Count == 0)
        {
            error = "The command does not identify a Railway service.";
            return false;
        }
        target = parsed;
        return true;
    }

    private static List<string> LoadTargetArguments()
    {
        if (!File.Exists(TargetFile)) return null;
        try
        {
            string[] lines = File.ReadAllLines(TargetFile, Encoding.UTF8);
            List<string> target = new List<string>();
            for (int index = 0; index < lines.Length; index++)
            {
                string token = lines[index].Trim();
                if (token.Length == 0 || token == "--" || !SafeTargetArgument.IsMatch(token))
                {
                    return null;
                }
                target.Add(token);
            }
            return target.Count == 0 ? null : target;
        }
        catch
        {
            return null;
        }
    }

    private static string ReadUuid()
    {
        Console.Write("Minecraft UUID (blank to cancel): ");
        string input = (Console.ReadLine() ?? "").Trim();
        if (input.Length == 0) return null;
        Guid uuid;
        if (!Guid.TryParse(input, out uuid))
        {
            WriteError("That is not a valid Minecraft UUID.");
            return null;
        }
        return uuid.ToString("D");
    }

    private static bool Confirm(string question)
    {
        Console.Write(question + " [y/N]: ");
        string response = (Console.ReadLine() ?? "").Trim();
        return string.Equals(response, "y", StringComparison.OrdinalIgnoreCase)
            || string.Equals(response, "yes", StringComparison.OrdinalIgnoreCase);
    }

    private static string[] SplitCommandLine(string commandLine)
    {
        int count;
        IntPtr pointer = CommandLineToArgvW(commandLine, out count);
        if (pointer == IntPtr.Zero)
        {
            throw new InvalidOperationException("Windows rejected the command line.");
        }
        try
        {
            string[] result = new string[count];
            for (int index = 0; index < count; index++)
            {
                IntPtr item = Marshal.ReadIntPtr(pointer, index * IntPtr.Size);
                result[index] = Marshal.PtrToStringUni(item) ?? "";
            }
            return result;
        }
        finally
        {
            LocalFree(pointer);
        }
    }

    private static string JoinArguments(List<string> arguments)
    {
        StringBuilder result = new StringBuilder();
        for (int index = 0; index < arguments.Count; index++)
        {
            if (index > 0) result.Append(' ');
            result.Append(QuoteWindowsArgument(arguments[index]));
        }
        return result.ToString();
    }

    private static string QuoteWindowsArgument(string value)
    {
        if (value.Length > 0 && value.IndexOfAny(new[] { ' ', '\t', '\n', '\v', '"' }) < 0)
        {
            return value;
        }

        StringBuilder result = new StringBuilder();
        result.Append('"');
        int backslashes = 0;
        for (int index = 0; index < value.Length; index++)
        {
            char character = value[index];
            if (character == '\\')
            {
                backslashes++;
                continue;
            }
            if (character == '"')
            {
                result.Append('\\', backslashes * 2 + 1);
                result.Append('"');
                backslashes = 0;
                continue;
            }
            result.Append('\\', backslashes);
            backslashes = 0;
            result.Append(character);
        }
        result.Append('\\', backslashes * 2);
        result.Append('"');
        return result.ToString();
    }

    private static string QuoteForCmd(string command)
    {
        return "\"" + command.Replace("\"", "\"\"") + "\"";
    }

    private static void WriteSuccess(string message)
    {
        Console.ForegroundColor = ConsoleColor.Green;
        Console.WriteLine(message);
        Console.ResetColor();
    }

    private static void WriteWarning(string message)
    {
        Console.ForegroundColor = ConsoleColor.Yellow;
        Console.WriteLine(message);
        Console.ResetColor();
    }

    private static void WriteError(string message)
    {
        Console.ForegroundColor = ConsoleColor.Red;
        Console.WriteLine(message);
        Console.ResetColor();
    }

    private static void Pause()
    {
        Console.WriteLine();
        Console.Write("Press Enter to continue...");
        Console.ReadLine();
    }

    private static int SelfTest()
    {
        try
        {
            List<string> target;
            string error;
            bool accepted = TryExtractTargetArguments(
                "railway ssh -p 11111111-1111-1111-1111-111111111111 -s backend -e production",
                out target,
                out error);
            if (!accepted || target == null || target.Count != 6)
            {
                Console.Error.WriteLine("Valid target test failed: " + error);
                return 1;
            }

            bool rejected = !TryExtractTargetArguments(
                "railway ssh -s backend -- sh -c malicious",
                out target,
                out error);
            if (!rejected)
            {
                Console.Error.WriteLine("Remote command rejection test failed.");
                return 1;
            }

            Guid uuid;
            if (!Guid.TryParse("12345678-1234-1234-1234-123456789abc", out uuid))
            {
                Console.Error.WriteLine("UUID validation test failed.");
                return 1;
            }

            string quoted = QuoteWindowsArgument("C:\\Program Files\\nodejs\\npm.cmd");
            if (!quoted.StartsWith("\"", StringComparison.Ordinal)
                || !quoted.EndsWith("\"", StringComparison.Ordinal))
            {
                Console.Error.WriteLine("Argument quoting test failed.");
                return 1;
            }

            Console.WriteLine(AppName + " " + Version + " self-test passed.");
            return 0;
        }
        catch (Exception exception)
        {
            Console.Error.WriteLine("Self-test exception: " + exception);
            return 1;
        }
    }
}
