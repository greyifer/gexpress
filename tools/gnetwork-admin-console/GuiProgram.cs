using System;
using System.Collections.Generic;
using System.Diagnostics;
using System.Drawing;
using System.Drawing.Drawing2D;
using System.IO;
using System.Reflection;
using System.Runtime.InteropServices;
using System.Text;
using System.Text.RegularExpressions;
using System.Threading;
using System.Web.Script.Serialization;
using System.Windows.Forms;

[assembly: AssemblyTitle("G'Network Admin Console")]
[assembly: AssemblyDescription("Private G'Network and G'Express owner control console")]
[assembly: AssemblyCompany("G'Network")]
[assembly: AssemblyProduct("G'Network Admin Console")]
[assembly: AssemblyCopyright("Copyright 2026")]
[assembly: AssemblyVersion("4.1.0.0")]
[assembly: AssemblyFileVersion("4.1.0.0")]

internal static class GuiProgram
{
    [STAThread]
    public static int Main(string[] args)
    {
        Application.EnableVisualStyles();
        Application.SetCompatibleTextRenderingDefault(false);

        if (args.Length > 0 && string.Equals(args[0], "--self-test", StringComparison.OrdinalIgnoreCase))
        {
            return AdminRuntime.SelfTest();
        }
        if (args.Length > 0 && string.Equals(args[0], "--backend-shell-self-test", StringComparison.OrdinalIgnoreCase))
        {
            return AdminRuntime.BackendShellSelfTest();
        }
        if (args.Length >= 2 && string.Equals(args[0], "--render-preview", StringComparison.OrdinalIgnoreCase))
        {
            return RenderPreview(args[1], args.Length >= 3 ? args[2] : null);
        }
        Application.Run(new MainWindow());
        return 0;
    }

    private static int RenderPreview(string outputPath, string pageName)
    {
        try
        {
            if (string.Equals(pageName, "Password", StringComparison.OrdinalIgnoreCase))
            {
                return SecurePasswordDialog.RenderPreview(outputPath);
            }
            using (MainWindow form = new MainWindow())
            {
                bool loadingPreview = string.Equals(pageName, "Loading", StringComparison.OrdinalIgnoreCase);
                bool transitionPreview = string.Equals(pageName, "Transition", StringComparison.OrdinalIgnoreCase);
                form.SelectPreviewPage(loadingPreview || transitionPreview ? "Overview" : pageName);
                form.Show();
                Application.DoEvents();
                if (loadingPreview) form.ShowPreviewLoading();
                if (transitionPreview) form.ShowPreviewTransition();
                int previewMilliseconds = transitionPreview ? 900 : loadingPreview ? 320 : 420;
                DateTime previewUntil = DateTime.UtcNow.AddMilliseconds(previewMilliseconds);
                while (DateTime.UtcNow < previewUntil)
                {
                    Application.DoEvents();
                    Thread.Sleep(15);
                }
                using (Bitmap bitmap = new Bitmap(form.Width, form.Height))
                {
                    form.DrawToBitmap(bitmap, new Rectangle(0, 0, bitmap.Width, bitmap.Height));
                    bitmap.Save(outputPath, System.Drawing.Imaging.ImageFormat.Png);
                }
                form.Close();
            }
            return 0;
        }
        catch
        {
            return 1;
        }
    }
}

internal static class Theme
{
    public static readonly Color Backdrop = Color.FromArgb(5, 9, 14);
    public static readonly Color Panel = Color.FromArgb(17, 22, 30);
    public static readonly Color Surface = Color.FromArgb(26, 33, 44);
    public static readonly Color SurfaceHover = Color.FromArgb(36, 46, 58);
    public static readonly Color Line = Color.FromArgb(64, 75, 92);
    public static readonly Color Accent = Color.FromArgb(255, 200, 87);
    public static readonly Color Blue = Color.FromArgb(103, 168, 255);
    public static readonly Color Purple = Color.FromArgb(157, 72, 255);
    public static readonly Color Text = Color.FromArgb(245, 247, 250);
    public static readonly Color Muted = Color.FromArgb(147, 158, 172);
    public static readonly Color Faint = Color.FromArgb(104, 116, 131);
    public static readonly Color Danger = Color.FromArgb(255, 113, 141);
    public static readonly Color Success = Color.FromArgb(76, 211, 145);

    public static Font Font(float size, FontStyle style)
    {
        return new Font("Segoe UI", size, style, GraphicsUnit.Point);
    }
}

internal enum PageKind
{
    Overview,
    Accounts,
    Revoke,
    Audit,
    Website,
    Backend,
    GameServers,
    Shop,
    Developer
}

internal sealed partial class MainWindow : Form
{
    private const int SidebarWidth = 224;
    private const int HeaderHeight = 42;
    private const int FooterHeight = 34;

    private readonly Panel content = new Panel();
    private readonly Label footerStatus = new Label();
    private readonly Panel footerDot = new Panel();
    private readonly LoadingOverlay loadingOverlay = new LoadingOverlay();
    private readonly System.Windows.Forms.Timer pageEntranceTimer = new System.Windows.Forms.Timer();
    private readonly List<PageMotion> pageMotions = new List<PageMotion>();
    private readonly Dictionary<PageKind, NavButton> navigation = new Dictionary<PageKind, NavButton>();
    private SiteDocument websiteDocument;
    private TextBox websiteName;
    private TextBox websiteAccent;
    private TextBox websiteBackground;
    private TextBox websitePanel;
    private ComboBox websitePage;
    private TextBox websiteTitle;
    private TextBox websiteNavLabel;
    private TextBox websiteSlug;
    private TextBox websiteEyebrow;
    private TextBox websiteHeadline;
    private TextBox websiteBody;
    private CheckBox websitePublished;
    private CheckBox websiteShowInNav;
    private int websiteSelectedPageIndex = -1;
    private bool websiteFieldsLoading;
    private PageKind currentPage = PageKind.Overview;
    private DateTime pageEntranceStarted;
    private bool maximized;
    private Rectangle restoredBounds;

    [DllImport("user32.dll")]
    private static extern bool ReleaseCapture();

    [DllImport("user32.dll")]
    private static extern IntPtr SendMessage(IntPtr window, int message, int parameter, int value);

    public MainWindow()
    {
        Text = "G'Network Admin Console";
        ClientSize = new Size(1160, 740);
        MinimumSize = new Size(980, 640);
        StartPosition = FormStartPosition.CenterScreen;
        FormBorderStyle = FormBorderStyle.None;
        BackColor = Theme.Backdrop;
        DoubleBuffered = true;
        Icon = Icon.ExtractAssociatedIcon(Application.ExecutablePath);

        pageEntranceTimer.Interval = 15;
        pageEntranceTimer.Tick += AnimatePageEntrance;
        BuildChrome();
        ShowPage(PageKind.Overview);
    }

    public void SelectPreviewPage(string pageName)
    {
        PageKind page;
        if (!string.IsNullOrWhiteSpace(pageName) &&
            Enum.TryParse<PageKind>(pageName, true, out page))
        {
            ShowPage(page);
        }
    }

    public void ShowPreviewLoading()
    {
        StartBusy("Connecting to the secure backend…", Theme.Purple);
    }

    public void ShowPreviewTransition()
    {
        NavigateTo(PageKind.Accounts);
    }

    protected override void OnPaint(PaintEventArgs e)
    {
        base.OnPaint(e);
        using (Pen border = new Pen(Theme.Line))
        {
            e.Graphics.DrawRectangle(border, 0, 0, ClientSize.Width - 1, ClientSize.Height - 1);
        }
    }

    private void BuildChrome()
    {
        SuspendLayout();

        Panel header = new Panel();
        header.Location = new Point(1, 1);
        header.Size = new Size(ClientSize.Width - 2, HeaderHeight);
        header.Anchor = AnchorStyles.Top | AnchorStyles.Left | AnchorStyles.Right;
        header.BackColor = Color.FromArgb(13, 18, 25);
        header.MouseDown += DragWindow;
        Controls.Add(header);

        Label windowTitle = LabelOf("G'Network  /  Administration", 10.5F, FontStyle.Bold, Theme.Muted);
        windowTitle.AutoSize = true;
        windowTitle.Location = new Point(18, 12);
        windowTitle.MouseDown += DragWindow;
        header.Controls.Add(windowTitle);

        WindowControlButton minimize = new WindowControlButton(WindowControlKind.Minimize);
        minimize.Size = new Size(44, HeaderHeight);
        minimize.Dock = DockStyle.Right;
        minimize.Click += delegate { WindowState = FormWindowState.Minimized; };
        header.Controls.Add(minimize);

        WindowControlButton maximize = new WindowControlButton(WindowControlKind.Maximize);
        maximize.Size = new Size(44, HeaderHeight);
        maximize.Dock = DockStyle.Right;
        maximize.Click += delegate
        {
            ToggleMaximize();
            maximize.ShowRestoreIcon = maximized;
        };
        header.Controls.Add(maximize);

        WindowControlButton close = new WindowControlButton(WindowControlKind.Close);
        close.Size = new Size(44, HeaderHeight);
        close.Dock = DockStyle.Right;
        close.Click += delegate { Close(); };
        header.Controls.Add(close);

        Panel footer = new Panel();
        footer.Location = new Point(1, ClientSize.Height - FooterHeight - 1);
        footer.Size = new Size(ClientSize.Width - 2, FooterHeight);
        footer.Anchor = AnchorStyles.Bottom | AnchorStyles.Left | AnchorStyles.Right;
        footer.BackColor = Color.FromArgb(13, 18, 25);
        Controls.Add(footer);

        footerDot.Size = new Size(8, 8);
        footerDot.Location = new Point(SidebarWidth + 18, 13);
        footerDot.BackColor = Theme.Blue;
        footer.Controls.Add(footerDot);

        footerStatus.AutoSize = true;
        footerStatus.Font = Theme.Font(8.5F, FontStyle.Regular);
        footerStatus.ForeColor = Theme.Muted;
        footerStatus.Location = new Point(SidebarWidth + 32, 9);
        footerStatus.Text = "Ready · local secrets are Windows-user encrypted";
        footer.Controls.Add(footerStatus);

        Label version = LabelOf("G'Network Admin 4.1", 8F, FontStyle.Regular, Theme.Faint);
        version.AutoSize = true;
        version.Anchor = AnchorStyles.Top | AnchorStyles.Right;
        version.Location = new Point(ClientSize.Width - 155, 9);
        footer.Resize += delegate { version.Left = footer.ClientSize.Width - version.Width - 18; };
        footer.Controls.Add(version);

        Panel sidebar = new Panel();
        sidebar.Location = new Point(1, HeaderHeight + 1);
        sidebar.Size = new Size(SidebarWidth, ClientSize.Height - HeaderHeight - FooterHeight - 2);
        sidebar.Anchor = AnchorStyles.Top | AnchorStyles.Bottom | AnchorStyles.Left;
        sidebar.BackColor = Theme.Panel;
        Controls.Add(sidebar);

        AddBrand(sidebar);
        AddNavigation(sidebar);
        AddPrivacyNotice(sidebar);

        content.Location = new Point(SidebarWidth + 1, HeaderHeight + 1);
        content.Size = new Size(
            ClientSize.Width - SidebarWidth - 2,
            ClientSize.Height - HeaderHeight - FooterHeight - 2);
        content.Anchor = AnchorStyles.Top | AnchorStyles.Bottom | AnchorStyles.Left | AnchorStyles.Right;
        content.BackColor = Theme.Backdrop;
        content.AutoScroll = true;
        Controls.Add(content);

        loadingOverlay.Location = content.Location;
        loadingOverlay.Size = content.Size;
        loadingOverlay.Anchor = content.Anchor;
        loadingOverlay.Visible = false;
        Controls.Add(loadingOverlay);

        ResumeLayout(true);
    }

    private void AddBrand(Panel sidebar)
    {
        Panel brand = new Panel();
        brand.Location = new Point(0, 0);
        brand.Size = new Size(SidebarWidth, 108);
        brand.BackColor = Theme.Panel;
        sidebar.Controls.Add(brand);

        PictureBox logo = new PictureBox();
        logo.Location = new Point(18, 20);
        logo.Size = new Size(62, 62);
        logo.SizeMode = PictureBoxSizeMode.Zoom;
        logo.Image = LoadLogo();
        brand.Controls.Add(logo);

        Label name = LabelOf("G'NETWORK", 13F, FontStyle.Bold, Theme.Text);
        name.AutoSize = true;
        name.Location = new Point(92, 29);
        brand.Controls.Add(name);

        Label sub = LabelOf("ADMIN CONTROL", 8F, FontStyle.Bold, Theme.Purple);
        sub.AutoSize = true;
        sub.Location = new Point(93, 54);
        brand.Controls.Add(sub);

        Panel line = new Panel();
        line.BackColor = Theme.Line;
        line.Location = new Point(18, 102);
        line.Size = new Size(SidebarWidth - 36, 1);
        brand.Controls.Add(line);
    }

    private void AddNavigation(Panel sidebar)
    {
        Label navTitle = LabelOf("NAVIGATION", 7.5F, FontStyle.Bold, Theme.Faint);
        navTitle.AutoSize = true;
        navTitle.Location = new Point(20, 126);
        sidebar.Controls.Add(navTitle);

        AddNav(sidebar, PageKind.Overview, "◆   Overview", 126);
        AddNav(sidebar, PageKind.Accounts, "●   Accounts", 161);
        AddNav(sidebar, PageKind.Revoke, "×   Revoke access", 196);
        AddNav(sidebar, PageKind.Audit, "◎   Audit history", 231);
        AddNav(sidebar, PageKind.Website, "W   Website", 266);
        AddNav(sidebar, PageKind.Backend, "⚙   Backend", 301);
        AddNav(sidebar, PageKind.GameServers, "S   Game servers", 336);
        AddNav(sidebar, PageKind.Shop, "$   Skin shop", 371);
        AddNav(sidebar, PageKind.Developer, "D   Developer", 406);
    }

    private void AddNav(Panel sidebar, PageKind page, string text, int top)
    {
        NavButton button = new NavButton(text);
        button.Location = new Point(12, top);
        button.Size = new Size(SidebarWidth - 24, 40);
        button.Click += delegate { NavigateTo(page); };
        sidebar.Controls.Add(button);
        navigation[page] = button;
    }

    private void AddPrivacyNotice(Panel sidebar)
    {
        Panel card = new Panel();
        card.Anchor = AnchorStyles.Left | AnchorStyles.Right | AnchorStyles.Bottom;
        card.Location = new Point(14, 489);
        card.Size = new Size(SidebarWidth - 28, 90);
        card.BackColor = Theme.Surface;
        card.Paint += delegate(object sender, PaintEventArgs e)
        {
            using (Pen pen = new Pen(Color.FromArgb(70, Theme.Purple)))
            {
                e.Graphics.DrawRectangle(pen, 0, 0, card.Width - 1, card.Height - 1);
            }
        };
        sidebar.Resize += delegate { card.Top = sidebar.ClientSize.Height - card.Height - 18; };
        sidebar.Controls.Add(card);

        Label shield = LabelOf("OWNER-PROTECTED", 8F, FontStyle.Bold, Theme.Purple);
        shield.AutoSize = true;
        shield.Location = new Point(14, 14);
        card.Controls.Add(shield);

        Label text = LabelOf(
            "Tokens are encrypted for\nthis Windows user.",
            8.5F,
            FontStyle.Regular,
            Theme.Muted);
        text.AutoSize = true;
        text.Location = new Point(14, 38);
        card.Controls.Add(text);
    }

    private void ShowPage(PageKind page)
    {
        ShowPage(page, true);
    }

    private void ShowPage(PageKind page, bool animate)
    {
        StopPageEntrance();
        currentPage = page;
        foreach (KeyValuePair<PageKind, NavButton> item in navigation)
        {
            item.Value.Selected = item.Key == page;
        }

        content.SuspendLayout();
        content.Controls.Clear();
        content.AutoScrollPosition = new Point(0, 0);

        switch (page)
        {
            case PageKind.Accounts:
                RenderAccounts();
                break;
            case PageKind.Revoke:
                RenderRevoke();
                break;
            case PageKind.Audit:
                RenderAudit();
                break;
            case PageKind.Website:
                RenderWebsite();
                break;
            case PageKind.Backend:
                RenderBackend();
                break;
            case PageKind.GameServers:
                RenderGameServers();
                break;
            case PageKind.Shop:
                RenderShop();
                break;
            case PageKind.Developer:
                RenderDeveloper();
                break;
            default:
                RenderOverview();
                break;
        }

        content.ResumeLayout(true);
        if (animate) StartPageEntrance();
    }

    private void NavigateTo(PageKind page)
    {
        if (page == currentPage || loadingOverlay.Active) return;

        Color accent = NavigationAccent(page);
        string name = NavigationName(page);

        loadingOverlay.BeginTransition(
            CaptureContent(),
            "Opening " + name + "…",
            accent,
            delegate
            {
                ShowPage(page, false);
                return CaptureContent();
            });
    }

    private void RenderOverview()
    {
        AddPageHeader("Moderation dashboard", "Private control surface for G'Network access", Theme.Accent);

        int available = Math.Max(760, content.ClientSize.Width - 72);
        int cardWidth = (available - 28) / 3;
        int left = 30;

        string railwayPath = AdminRuntime.FindRailway();
        string railwayVersion = AdminRuntime.GetRailwayVersion();
        bool configured = AdminRuntime.LoadTargetArguments() != null;

        content.Controls.Add(StatusCard(
            left,
            102,
            cardWidth,
            "RAILWAY CLI",
            railwayVersion != null ? "Ready" : railwayPath != null ? "Repair needed" : "Needs setup",
            railwayVersion != null
                ? railwayVersion
                : railwayPath != null
                    ? "A broken launcher was detected."
                    : "Install and authenticate once.",
            railwayVersion != null ? Theme.Success : Theme.Danger));

        content.Controls.Add(StatusCard(
            left + cardWidth + 14,
            102,
            cardWidth,
            "BACKEND TARGET",
            configured ? "Configured" : "Not configured",
            configured ? "Production service target saved." : "Paste Railway's SSH command.",
            configured ? Theme.Blue : Theme.Danger));

        content.Controls.Add(StatusCard(
            left + (cardWidth + 14) * 2,
            102,
            cardWidth,
            "SECURITY",
            "UUID-bound",
            "Passwords and UUIDs are never retained.",
            Theme.Purple));

        Label quick = LabelOf("QUICK ACTIONS", 8F, FontStyle.Bold, Theme.Faint);
        quick.AutoSize = true;
        quick.Location = new Point(left, 260);
        content.Controls.Add(quick);

        GButton create = new GButton("Create moderator", Theme.Panel, Theme.SurfaceHover);
        create.AccentColor = Theme.Accent;
        create.Location = new Point(left, 286);
        create.Size = new Size(176, 46);
        create.Click += delegate { NavigateTo(PageKind.Accounts); };
        content.Controls.Add(create);

        GButton revoke = new GButton("Revoke access", Theme.Panel, Theme.SurfaceHover);
        revoke.AccentColor = Theme.Danger;
        revoke.Location = new Point(left + 188, 286);
        revoke.Size = new Size(156, 46);
        revoke.Click += delegate { NavigateTo(PageKind.Revoke); };
        content.Controls.Add(revoke);

        GButton website = new GButton("Edit website", Theme.Panel, Theme.SurfaceHover);
        website.AccentColor = Theme.Success;
        website.Location = new Point(left + 356, 286);
        website.Size = new Size(156, 46);
        website.Click += delegate { NavigateTo(PageKind.Website); };
        content.Controls.Add(website);

        GButton backend = new GButton("Backend setup", Theme.Panel, Theme.SurfaceHover);
        backend.AccentColor = Theme.Blue;
        backend.Location = new Point(left + 524, 286);
        backend.Size = new Size(156, 46);
        backend.Click += delegate { NavigateTo(PageKind.Backend); };
        content.Controls.Add(backend);

        Panel workflow = Card(left, 366, available, 220, Theme.Accent);
        content.Controls.Add(workflow);
        AddCardTitle(workflow, "How access is granted", "The same protected flow used by the in-game G'Lib moderator view.");

        AddStep(workflow, 1, "Player verifies their Minecraft account", "They open K > Friends & Chat once.", 80);
        AddStep(workflow, 2, "You provision the verified UUID", "Choose a role here and enter the exact UUID.", 124);
        AddStep(workflow, 3, "They replace the temporary password", "The first login forces a private password change.", 168);
    }

    private void RenderAccounts()
    {
        AddPageHeader("Provision access", "Create or reset a UUID-bound moderator account", Theme.Accent);

        Panel form = Card(30, 102, 520, 430, Theme.Accent);
        content.Controls.Add(form);
        AddCardTitle(form, "Moderator account", "The player must have opened Friends & Chat once.");

        Label uuidLabel = LabelOf("MINECRAFT UUID", 8F, FontStyle.Bold, Theme.Muted);
        uuidLabel.AutoSize = true;
        uuidLabel.Location = new Point(24, 88);
        form.Controls.Add(uuidLabel);

        TextBox uuid = TextField(24, 110, 470);
        form.Controls.Add(uuid);

        Label roleLabel = LabelOf("ACCESS ROLE", 8F, FontStyle.Bold, Theme.Muted);
        roleLabel.AutoSize = true;
        roleLabel.Location = new Point(24, 166);
        form.Controls.Add(roleLabel);

        string selectedRole = "MODERATOR";
        GButton moderator = new GButton("MODERATOR", Theme.Accent, Theme.SurfaceHover);
        moderator.ForeColor = Theme.Backdrop;
        moderator.Location = new Point(24, 190);
        moderator.Size = new Size(224, 40);
        form.Controls.Add(moderator);

        GButton owner = new GButton("OWNER", Theme.Surface, Theme.SurfaceHover);
        owner.Location = new Point(258, 190);
        owner.Size = new Size(236, 40);
        form.Controls.Add(owner);

        moderator.Click += delegate
        {
            selectedRole = "MODERATOR";
            moderator.SetPalette(Theme.Accent, Color.FromArgb(255, 216, 125), Theme.Backdrop);
            owner.SetPalette(Theme.Surface, Theme.SurfaceHover, Theme.Text);
        };
        owner.Click += delegate
        {
            selectedRole = "OWNER";
            owner.SetPalette(Theme.Accent, Color.FromArgb(255, 216, 125), Theme.Backdrop);
            moderator.SetPalette(Theme.Surface, Theme.SurfaceHover, Theme.Text);
        };

        Panel note = SoftPanel(24, 250, 470, 80, Theme.Blue);
        form.Controls.Add(note);
        Label noteText = LabelOf(
            "The initial password stays inside this protected window.\nIt travels through encrypted SSH input and is never saved.",
            9F,
            FontStyle.Regular,
            Theme.Muted);
        noteText.AutoSize = true;
        noteText.Location = new Point(18, 18);
        note.Controls.Add(noteText);

        GButton submit = new GButton("Create / reset account", Theme.Accent, Color.FromArgb(255, 216, 125));
        submit.ForeColor = Theme.Backdrop;
        submit.Location = new Point(24, 352);
        submit.Size = new Size(470, 48);
        submit.Font = Theme.Font(10F, FontStyle.Bold);
        submit.Click += delegate { GrantAccount(selectedRole, uuid.Text); };
        form.Controls.Add(submit);

        Panel guide = Card(574, 102, 340, 430, Theme.Purple);
        content.Controls.Add(guide);
        AddCardTitle(guide, "First-login flow", "What the staff member experiences.");
        AddStep(guide, 1, "Open Friends & Chat", "The Admin button appears for their UUID.", 92);
        AddStep(guide, 2, "Enter initial password", "It only unlocks this verified account.", 160);
        AddStep(guide, 3, "Choose a new password", "At least 12 characters; other sessions revoke.", 228);
        AddStep(guide, 4, "Use moderation view", "Users, DMs, groups, reports, and audit logs.", 296);
    }

    private void RenderRevoke()
    {
        AddPageHeader("Revoke access", "Immediately disable an admin account and its active sessions", Theme.Danger);

        Panel form = Card(30, 102, 560, 330, Theme.Danger);
        content.Controls.Add(form);
        AddCardTitle(form, "Disable moderator", "This action is immediate and requires confirmation.");

        Label uuidLabel = LabelOf("MINECRAFT UUID", 8F, FontStyle.Bold, Theme.Muted);
        uuidLabel.AutoSize = true;
        uuidLabel.Location = new Point(24, 92);
        form.Controls.Add(uuidLabel);

        TextBox uuid = TextField(24, 114, 510);
        form.Controls.Add(uuid);

        Panel warning = SoftPanel(24, 170, 510, 64, Theme.Danger);
        form.Controls.Add(warning);
        Label warningText = LabelOf(
            "The account is disabled and all active moderator sessions are deleted.",
            9F,
            FontStyle.Regular,
            Theme.Muted);
        warningText.MaximumSize = new Size(470, 0);
        warningText.AutoSize = true;
        warningText.Location = new Point(18, 18);
        warning.Controls.Add(warningText);

        GButton submit = new GButton("Revoke admin access", Theme.Danger, Color.FromArgb(255, 135, 158));
        submit.ForeColor = Theme.Backdrop;
        submit.Location = new Point(24, 256);
        submit.Size = new Size(510, 46);
        submit.Font = Theme.Font(10F, FontStyle.Bold);
        submit.Click += delegate { RevokeAccount(uuid.Text); };
        form.Controls.Add(submit);

        Panel restore = Card(616, 102, 298, 330, Theme.Blue);
        content.Controls.Add(restore);
        AddCardTitle(restore, "Restore later", "Revocation does not delete normal G'Network data.");
        Label body = LabelOf(
            "To restore access, return to Accounts and create the account again.\n\nA new initial password will be required and the player must replace it on first login.",
            9F,
            FontStyle.Regular,
            Theme.Muted);
        body.MaximumSize = new Size(246, 0);
        body.AutoSize = true;
        body.Location = new Point(24, 92);
        restore.Controls.Add(body);
    }

    private void RenderAudit()
    {
        AddPageHeader(
            "Owner audit history",
            "Who accessed moderation data, when, and for what purpose",
            Theme.Purple);

        Panel card = Card(30, 102, 884, 470, Theme.Purple);
        content.Controls.Add(card);
        AddCardTitle(
            card,
            "Private access ledger",
            "Loaded directly from production through your local Railway SSH key.");

        Label privacy = LabelOf(
            "This owner-only console never requests or displays message bodies. The ledger contains moderator, action, target type, target reference, and time.",
            8.5F,
            FontStyle.Regular,
            Theme.Muted);
        privacy.Location = new Point(24, 74);
        privacy.MaximumSize = new Size(830, 0);
        privacy.AutoSize = true;
        card.Controls.Add(privacy);

        RichTextBox ledger = new RichTextBox();
        ledger.Location = new Point(24, 112);
        ledger.Size = new Size(836, 286);
        ledger.Anchor = AnchorStyles.Top | AnchorStyles.Bottom | AnchorStyles.Left | AnchorStyles.Right;
        ledger.BackColor = Color.FromArgb(7, 11, 16);
        ledger.ForeColor = Theme.Text;
        ledger.BorderStyle = BorderStyle.FixedSingle;
        ledger.Font = new Font("Consolas", 9.25F, FontStyle.Regular, GraphicsUnit.Point);
        ledger.ReadOnly = true;
        ledger.DetectUrls = false;
        ledger.WordWrap = false;
        ledger.Text = "Select Refresh audit history to load the latest production access events.";
        card.Controls.Add(ledger);

        GButton refresh = new GButton("Refresh audit history", Theme.Purple, Color.FromArgb(181, 104, 255));
        refresh.Location = new Point(24, 414);
        refresh.Size = new Size(218, 38);
        refresh.Font = Theme.Font(9.5F, FontStyle.Bold);
        refresh.Click += delegate { LoadAuditHistory(ledger); };
        card.Controls.Add(refresh);

        Label hint = LabelOf(
            "No audit event is cached or written to disk.",
            8F,
            FontStyle.Regular,
            Theme.Faint);
        hint.AutoSize = true;
        hint.Location = new Point(258, 426);
        card.Controls.Add(hint);
    }

    private void LoadAuditHistory(RichTextBox ledger)
    {
        if (!RequireBackend()) return;
        List<string> remote = new List<string>();
        remote.Add("env");
        remote.Add("ADMIN_ACTION=audit");
        remote.Add("ADMIN_AUDIT_LIMIT=60");
        remote.Add("node");
        remote.Add("/app/dist/admin-cli.js");

        SetStatus("Loading private production audit history…", Theme.Purple);
        StartBusy("Reading owner audit ledger…", Theme.Purple);
        AdminRuntime.RunRemoteLongCapturedAsync(remote, delegate(RemoteCommandResult result)
        {
            if (IsDisposed) return;
            BeginInvoke((MethodInvoker)delegate
            {
                if (result.ExitCode == 0)
                {
                    ledger.Text = FormatAuditLedger(result.Message);
                    ledger.SelectionStart = 0;
                    ledger.ScrollToCaret();
                    SetStatus("Audit history loaded · nothing was stored locally.", Theme.Success);
                    FinishBusy("Audit history ready", Theme.Success);
                }
                else
                {
                    ledger.Text = result.Message;
                    SetStatus("Audit history could not be loaded.", Theme.Danger);
                    FinishBusy("Audit history failed", Theme.Danger);
                }
            });
        });
    }

    private static string FormatAuditLedger(string raw)
    {
        if (string.IsNullOrWhiteSpace(raw)) return "No moderation audit events were found.";
        StringBuilder formatted = new StringBuilder();
        string[] lines = raw.Split(new[] { '\r', '\n' }, StringSplitOptions.RemoveEmptyEntries);
        for (int index = 0; index < lines.Length; index++)
        {
            string[] fields = lines[index].Split('\t');
            if (fields.Length < 4) continue;
            string time = fields[0].Replace("T", " ");
            if (time.EndsWith("Z", StringComparison.Ordinal)) time = time.Substring(0, time.Length - 1) + " UTC";
            string actor = fields[1];
            string role = fields.Length > 2 ? fields[2] : "";
            string action = fields.Length > 3 ? fields[3].Replace('_', ' ') : "";
            string targetType = fields.Length > 4 ? fields[4].Replace('_', ' ') : "";
            string target = fields.Length > 5 ? fields[5] : "";
            formatted.Append(time).Append("  ").Append(actor).Append("  [").Append(role).AppendLine("]");
            formatted.Append("  ").Append(action);
            if (targetType.Length > 0) formatted.Append(" · ").Append(targetType);
            if (target.Length > 0) formatted.Append(" · ").Append(target);
            formatted.AppendLine().AppendLine();
        }
        return formatted.Length == 0 ? "No readable audit events were returned." : formatted.ToString().TrimEnd();
    }

    private void RenderWebsite()
    {
        AddPageHeader("Website editor", "Edit production content here; saved changes appear on the live site automatically", Theme.Success);

        Panel actions = Card(30, 102, 884, 82, Theme.Success);
        content.Controls.Add(actions);

        GButton load = new GButton("Load live website", Theme.Success, Color.FromArgb(103, 225, 165));
        load.ForeColor = Theme.Backdrop;
        load.Location = new Point(20, 20);
        load.Size = new Size(172, 42);
        load.Click += delegate { LoadWebsite(); };
        actions.Controls.Add(load);

        GButton save = new GButton("Save & publish", Theme.Panel, Theme.SurfaceHover);
        save.AccentColor = Theme.Success;
        save.Location = new Point(204, 20);
        save.Size = new Size(160, 42);
        save.Enabled = websiteDocument != null;
        save.Click += delegate { SaveWebsite(); };
        actions.Controls.Add(save);

        GButton preview = new GButton("Open live site", Theme.Panel, Theme.SurfaceHover);
        preview.AccentColor = Theme.Blue;
        preview.Location = new Point(376, 20);
        preview.Size = new Size(150, 42);
        preview.Click += delegate { OpenWebsiteUrl("https://gnetwork-website-production.up.railway.app/"); };
        actions.Controls.Add(preview);

        GButton advanced = new GButton("Advanced builder", Theme.Panel, Theme.SurfaceHover);
        advanced.AccentColor = Theme.Purple;
        advanced.Location = new Point(538, 20);
        advanced.Size = new Size(164, 42);
        advanced.Click += delegate { OpenWebsiteUrl("https://gnetwork-website-production.up.railway.app/builder"); };
        actions.Controls.Add(advanced);

        Label live = LabelOf("LIVE", 7.5F, FontStyle.Bold, Theme.Success);
        live.AutoSize = true;
        live.Location = new Point(730, 26);
        actions.Controls.Add(live);
        Label liveDetail = LabelOf("Auto-refreshes\nopen browsers", 8F, FontStyle.Regular, Theme.Muted);
        liveDetail.AutoSize = true;
        liveDetail.Location = new Point(730, 42);
        actions.Controls.Add(liveDetail);

        Panel settings = Card(30, 202, 300, 430, Theme.Blue);
        content.Controls.Add(settings);
        AddCardTitle(settings, "Site settings", "Brand name and the colours used across every public page.");

        AddWebsiteFieldLabel(settings, "SITE NAME", 24, 86);
        websiteName = TextField(24, 106, 252);
        websiteName.MaxLength = 40;
        settings.Controls.Add(websiteName);

        AddWebsiteFieldLabel(settings, "ACCENT", 24, 157);
        websiteAccent = TextField(24, 177, 252);
        websiteAccent.MaxLength = 7;
        settings.Controls.Add(websiteAccent);

        AddWebsiteFieldLabel(settings, "BACKGROUND", 24, 228);
        websiteBackground = TextField(24, 248, 252);
        websiteBackground.MaxLength = 7;
        settings.Controls.Add(websiteBackground);

        AddWebsiteFieldLabel(settings, "PANELS", 24, 299);
        websitePanel = TextField(24, 319, 252);
        websitePanel.MaxLength = 7;
        settings.Controls.Add(websitePanel);

        Label colourHelp = LabelOf("Use six-digit hex colours, for example #4cdb8a.", 8F, FontStyle.Regular, Theme.Muted);
        colourHelp.MaximumSize = new Size(252, 0);
        colourHelp.AutoSize = true;
        colourHelp.Location = new Point(24, 370);
        settings.Controls.Add(colourHelp);

        Panel pageCard = Card(350, 202, 564, 570, Theme.Success);
        content.Controls.Add(pageCard);
        AddCardTitle(pageCard, "Page content", "Choose a page, edit it, then save once. Sections and buttons are kept intact.");

        AddWebsiteFieldLabel(pageCard, "PAGE", 24, 84);
        websitePage = new ComboBox();
        websitePage.Location = new Point(24, 104);
        websitePage.Size = new Size(516, 30);
        websitePage.DropDownStyle = ComboBoxStyle.DropDownList;
        websitePage.BackColor = Theme.Surface;
        websitePage.ForeColor = Theme.Text;
        websitePage.FlatStyle = FlatStyle.Flat;
        websitePage.Font = Theme.Font(9.5F, FontStyle.Regular);
        websitePage.SelectedIndexChanged += WebsitePageChanged;
        pageCard.Controls.Add(websitePage);

        AddWebsiteFieldLabel(pageCard, "PAGE TITLE", 24, 148);
        websiteTitle = TextField(24, 168, 248);
        websiteTitle.MaxLength = 80;
        pageCard.Controls.Add(websiteTitle);
        AddWebsiteFieldLabel(pageCard, "NAVIGATION LABEL", 292, 148);
        websiteNavLabel = TextField(292, 168, 248);
        websiteNavLabel.MaxLength = 24;
        pageCard.Controls.Add(websiteNavLabel);

        AddWebsiteFieldLabel(pageCard, "URL SLUG", 24, 218);
        websiteSlug = TextField(24, 238, 248);
        websiteSlug.MaxLength = 48;
        pageCard.Controls.Add(websiteSlug);
        AddWebsiteFieldLabel(pageCard, "SMALL HEADING", 292, 218);
        websiteEyebrow = TextField(292, 238, 248);
        websiteEyebrow.MaxLength = 40;
        pageCard.Controls.Add(websiteEyebrow);

        AddWebsiteFieldLabel(pageCard, "HEADLINE", 24, 288);
        websiteHeadline = TextField(24, 308, 516);
        websiteHeadline.MaxLength = 100;
        pageCard.Controls.Add(websiteHeadline);

        AddWebsiteFieldLabel(pageCard, "INTRO TEXT", 24, 358);
        websiteBody = TextField(24, 378, 516);
        websiteBody.Multiline = true;
        websiteBody.ScrollBars = ScrollBars.Vertical;
        websiteBody.Size = new Size(516, 100);
        websiteBody.MaxLength = 1200;
        pageCard.Controls.Add(websiteBody);

        websitePublished = WebsiteCheckBox("Published", 24, 500);
        pageCard.Controls.Add(websitePublished);
        websiteShowInNav = WebsiteCheckBox("Show in navigation", 154, 500);
        pageCard.Controls.Add(websiteShowInNav);

        Label editorHelp = LabelOf("Use Advanced builder to add, remove or reorder pages, sections and buttons.", 8F, FontStyle.Regular, Theme.Muted);
        editorHelp.AutoSize = true;
        editorHelp.Location = new Point(24, 538);
        pageCard.Controls.Add(editorHelp);

        SetWebsiteFieldsEnabled(websiteDocument != null);
        if (websiteDocument != null) PopulateWebsiteEditor();
    }

    private static void AddWebsiteFieldLabel(Control parent, string text, int left, int top)
    {
        Label label = LabelOf(text, 7.5F, FontStyle.Bold, Theme.Muted);
        label.AutoSize = true;
        label.Location = new Point(left, top);
        parent.Controls.Add(label);
    }

    private static CheckBox WebsiteCheckBox(string text, int left, int top)
    {
        CheckBox check = new CheckBox();
        check.Text = text;
        check.Location = new Point(left, top);
        check.AutoSize = true;
        check.Font = Theme.Font(9F, FontStyle.Bold);
        check.ForeColor = Theme.Text;
        check.BackColor = Theme.Panel;
        return check;
    }

    private void SetWebsiteFieldsEnabled(bool enabled)
    {
        Control[] fields = {
            websiteName, websiteAccent, websiteBackground, websitePanel, websitePage,
            websiteTitle, websiteNavLabel, websiteSlug, websiteEyebrow, websiteHeadline,
            websiteBody, websitePublished, websiteShowInNav
        };
        for (int index = 0; index < fields.Length; index++)
        {
            if (fields[index] != null) fields[index].Enabled = enabled;
        }
    }

    private void PopulateWebsiteEditor()
    {
        if (websiteDocument == null || websiteDocument.settings == null || websiteDocument.pages == null) return;
        websiteFieldsLoading = true;
        websiteName.Text = websiteDocument.settings.siteName ?? "";
        websiteAccent.Text = websiteDocument.settings.accent ?? "";
        websiteBackground.Text = websiteDocument.settings.background ?? "";
        websitePanel.Text = websiteDocument.settings.panel ?? "";
        int nextIndex = websiteSelectedPageIndex >= 0 && websiteSelectedPageIndex < websiteDocument.pages.Length
            ? websiteSelectedPageIndex
            : 0;
        websitePage.Items.Clear();
        for (int index = 0; index < websiteDocument.pages.Length; index++)
        {
            SitePageDocument page = websiteDocument.pages[index];
            string route = string.Equals(page.slug, "home", StringComparison.OrdinalIgnoreCase) ? "/" : "/p/" + page.slug;
            websitePage.Items.Add((page.navLabel ?? page.title ?? "Untitled") + "   " + route);
        }
        websitePage.SelectedIndex = websiteDocument.pages.Length == 0 ? -1 : nextIndex;
        websiteSelectedPageIndex = websitePage.SelectedIndex;
        PopulateWebsitePageFields();
        websiteFieldsLoading = false;
    }

    private void PopulateWebsitePageFields()
    {
        if (websiteDocument == null || websiteDocument.pages == null
            || websiteSelectedPageIndex < 0 || websiteSelectedPageIndex >= websiteDocument.pages.Length) return;
        SitePageDocument page = websiteDocument.pages[websiteSelectedPageIndex];
        websiteTitle.Text = page.title ?? "";
        websiteNavLabel.Text = page.navLabel ?? "";
        websiteSlug.Text = page.slug ?? "";
        websiteEyebrow.Text = page.eyebrow ?? "";
        websiteHeadline.Text = page.headline ?? "";
        websiteBody.Text = page.body ?? "";
        websitePublished.Checked = page.published;
        websiteShowInNav.Checked = page.showInNav;
        bool home = string.Equals(page.slug, "home", StringComparison.OrdinalIgnoreCase);
        websiteSlug.Enabled = !home;
        websitePublished.Enabled = !home;
        websiteShowInNav.Enabled = !home;
    }

    private void WebsitePageChanged(object sender, EventArgs eventArgs)
    {
        if (websiteFieldsLoading) return;
        ApplyWebsiteFields();
        websiteSelectedPageIndex = websitePage.SelectedIndex;
        websiteFieldsLoading = true;
        PopulateWebsitePageFields();
        websiteFieldsLoading = false;
    }

    private void ApplyWebsiteFields()
    {
        if (websiteDocument == null || websiteDocument.settings == null) return;
        websiteDocument.settings.siteName = websiteName.Text.Trim();
        websiteDocument.settings.accent = websiteAccent.Text.Trim();
        websiteDocument.settings.background = websiteBackground.Text.Trim();
        websiteDocument.settings.panel = websitePanel.Text.Trim();
        if (websiteDocument.pages == null || websiteSelectedPageIndex < 0
            || websiteSelectedPageIndex >= websiteDocument.pages.Length) return;
        SitePageDocument page = websiteDocument.pages[websiteSelectedPageIndex];
        page.title = websiteTitle.Text.Trim();
        page.navLabel = websiteNavLabel.Text.Trim();
        if (!string.Equals(page.slug, "home", StringComparison.OrdinalIgnoreCase)) page.slug = websiteSlug.Text.Trim().ToLowerInvariant();
        page.eyebrow = websiteEyebrow.Text.Trim();
        page.headline = websiteHeadline.Text.Trim();
        page.body = websiteBody.Text.Trim();
        if (!string.Equals(page.slug, "home", StringComparison.OrdinalIgnoreCase))
        {
            page.published = websitePublished.Checked;
            page.showInNav = websiteShowInNav.Checked;
        }
    }

    private void LoadWebsite()
    {
        if (!RequireBackend()) return;
        List<string> remote = new List<string>();
        remote.Add("env");
        remote.Add("ADMIN_ACTION=site-get");
        remote.Add("node");
        remote.Add("/app/dist/admin-cli.js");
        SetStatus("Loading production website…", Theme.Success);
        StartBusy("Loading website content…", Theme.Success);
        AdminRuntime.RunRemoteWebsiteCapturedAsync(remote, null, delegate(RemoteCommandResult result)
        {
            if (IsDisposed) return;
            BeginInvoke((MethodInvoker)delegate
            {
                if (result.ExitCode != 0)
                {
                    SetStatus("Website could not be loaded.", Theme.Danger);
                    FinishBusy("Website load failed", Theme.Danger);
                    GDialog.ShowMessage(this, "Website load failed", result.Message, Theme.Danger);
                    return;
                }
                try
                {
                    JavaScriptSerializer serializer = new JavaScriptSerializer();
                    serializer.MaxJsonLength = 512 * 1024;
                    SiteDocument loaded = serializer.Deserialize<SiteDocument>(result.Message);
                    if (loaded == null || loaded.settings == null || loaded.pages == null || loaded.pages.Length == 0)
                    {
                        throw new InvalidOperationException("The backend returned an incomplete website document.");
                    }
                    websiteDocument = loaded;
                    websiteSelectedPageIndex = 0;
                    ShowPage(PageKind.Website, false);
                    SetStatus("Website loaded from production.", Theme.Success);
                    FinishBusy("Website editor ready", Theme.Success);
                }
                catch (Exception exception)
                {
                    SetStatus("Website response could not be read.", Theme.Danger);
                    FinishBusy("Website load failed", Theme.Danger);
                    GDialog.ShowMessage(this, "Website response error", exception.Message, Theme.Danger);
                }
            });
        });
    }

    private void SaveWebsite()
    {
        if (websiteDocument == null || !RequireBackend()) return;
        ApplyWebsiteFields();
        if (websiteDocument.settings.siteName.Length == 0
            || !Regex.IsMatch(websiteDocument.settings.accent ?? "", "^#[0-9a-fA-F]{6}$")
            || !Regex.IsMatch(websiteDocument.settings.background ?? "", "^#[0-9a-fA-F]{6}$")
            || !Regex.IsMatch(websiteDocument.settings.panel ?? "", "^#[0-9a-fA-F]{6}$"))
        {
            GDialog.ShowMessage(this, "Check site settings", "Enter a site name and three six-digit hex colours before saving.", Theme.Danger);
            return;
        }
        JavaScriptSerializer serializer = new JavaScriptSerializer();
        serializer.MaxJsonLength = 512 * 1024;
        string json = serializer.Serialize(websiteDocument);
        List<string> remote = new List<string>();
        remote.Add("env");
        remote.Add("ADMIN_ACTION=site-put");
        remote.Add("node");
        remote.Add("/app/dist/admin-cli.js");
        SetStatus("Publishing website changes…", Theme.Success);
        StartBusy("Saving and publishing…", Theme.Success);
        AdminRuntime.RunRemoteWebsiteCapturedAsync(remote, json, delegate(RemoteCommandResult result)
        {
            if (IsDisposed) return;
            BeginInvoke((MethodInvoker)delegate
            {
                if (result.ExitCode != 0)
                {
                    SetStatus("Website save failed.", Theme.Danger);
                    FinishBusy("Website save failed", Theme.Danger);
                    GDialog.ShowMessage(this, "Website save failed", result.Message, Theme.Danger);
                    return;
                }
                try
                {
                    SiteDocument saved = serializer.Deserialize<SiteDocument>(result.Message);
                    if (saved != null) websiteDocument = saved;
                }
                catch { }
                ShowPage(PageKind.Website, false);
                SetStatus("Published — open browsers update automatically.", Theme.Success);
                FinishBusy("Website is live", Theme.Success);
            });
        });
    }

    private void OpenWebsiteUrl(string url)
    {
        try
        {
            ProcessStartInfo start = new ProcessStartInfo(url);
            start.UseShellExecute = true;
            Process.Start(start);
        }
        catch (Exception exception)
        {
            GDialog.ShowMessage(this, "Could not open browser", exception.Message, Theme.Danger);
        }
    }

    private void RenderBackend()
    {
        AddPageHeader("Backend connection", "Authenticated Railway access and production service targeting", Theme.Blue);

        string railwayPath = AdminRuntime.FindRailway();
        string railwayVersion = AdminRuntime.GetRailwayVersion();
        bool railwayReady = railwayVersion != null;
        bool sshKeyReady = AdminRuntime.HasLocalSshKey();
        string toolingDescription = railwayReady
            ? railwayVersion + (sshKeyReady ? " · SSH key ready" : " · SSH key required")
            : railwayPath != null
                ? "A Railway launcher exists, but it cannot start. Use Repair below."
                : "Node.js and Railway CLI still need setup.";

        Panel tools = Card(30, 102, 420, 300, Theme.Blue);
        content.Controls.Add(tools);
        AddCardTitle(tools, "Railway tooling", toolingDescription);

        GButton install = new GButton(
            railwayReady
                ? "Repair / reinstall"
                : railwayPath != null
                    ? "Repair Railway CLI"
                    : "Install required tools",
            railwayReady ? Theme.Surface : Theme.Blue,
            Theme.SurfaceHover);
        install.Location = new Point(24, 92);
        install.Size = new Size(180, 42);
        install.Click += delegate { InstallTools(); };
        tools.Controls.Add(install);

        GButton login = new GButton("Railway login", Theme.Surface, Theme.SurfaceHover);
        login.AccentColor = Theme.Accent;
        login.Location = new Point(214, 92);
        login.Size = new Size(180, 42);
        login.Click += delegate { RunRailwayUtility("login"); };
        tools.Controls.Add(login);

        GButton whoami = new GButton("Account status", Theme.Surface, Theme.SurfaceHover);
        whoami.AccentColor = Theme.Purple;
        whoami.Location = new Point(24, 146);
        whoami.Size = new Size(180, 42);
        whoami.Click += delegate { RunRailwayUtility("whoami"); };
        tools.Controls.Add(whoami);

        GButton shell = new GButton("In-app private shell", Theme.Surface, Theme.SurfaceHover);
        shell.AccentColor = Theme.Blue;
        shell.Location = new Point(214, 146);
        shell.Size = new Size(180, 42);
        shell.Click += delegate { OpenPrivateShell(); };
        tools.Controls.Add(shell);

        GButton sshSetup = new GButton(
            sshKeyReady ? "Register / repair SSH key" : "Set up SSH access",
            Theme.Surface,
            Theme.SurfaceHover);
        sshSetup.AccentColor = sshKeyReady ? Theme.Success : Theme.Danger;
        sshSetup.Location = new Point(24, 200);
        sshSetup.Size = new Size(370, 42);
        sshSetup.Click += delegate { SetupSshAccess(); };
        tools.Controls.Add(sshSetup);

        Panel target = Card(474, 102, 440, 388, Theme.Purple);
        content.Controls.Add(target);
        AddCardTitle(target, "Production target", AdminRuntime.LoadTargetArguments() == null
            ? "No backend service is configured."
            : "A Railway backend target is currently saved.");

        Label instruction = LabelOf(
            "Right-click the G'Network backend service in Railway and choose\n“Copy SSH Command”, then paste it below.",
            9F,
            FontStyle.Regular,
            Theme.Muted);
        instruction.AutoSize = true;
        instruction.Location = new Point(24, 82);
        target.Controls.Add(instruction);

        TextBox command = TextField(24, 132, 390);
        command.Multiline = true;
        command.Height = 84;
        command.ScrollBars = ScrollBars.Vertical;
        target.Controls.Add(command);

        GButton save = new GButton("Save backend target", Theme.Purple, Color.FromArgb(181, 104, 255));
        save.Location = new Point(24, 232);
        save.Size = new Size(390, 44);
        save.Font = Theme.Font(9.5F, FontStyle.Bold);
        save.Click += delegate { SaveBackendTarget(command.Text); };
        target.Controls.Add(save);

        GButton forget = new GButton("Forget saved target", Theme.Surface, Theme.SurfaceHover);
        forget.AccentColor = Theme.Danger;
        forget.Location = new Point(24, 288);
        forget.Size = new Size(390, 40);
        forget.Click += delegate { ForgetTarget(); };
        target.Controls.Add(forget);

        Label location = LabelOf(
            "%LOCALAPPDATA%\\GNetworkAdminConsole\\railway-target.txt",
            7.5F,
            FontStyle.Regular,
            Theme.Faint);
        location.AutoSize = true;
        location.Location = new Point(24, 344);
        target.Controls.Add(location);

        Panel security = Card(30, 426, 420, 118, Theme.Success);
        content.Controls.Add(security);
        Label secureTitle = LabelOf("LOCAL STORAGE POLICY", 8F, FontStyle.Bold, Theme.Success);
        secureTitle.AutoSize = true;
        secureTitle.Location = new Point(20, 18);
        security.Controls.Add(secureTitle);
        Label secureBody = LabelOf(
            "Only the non-secret Railway target is saved.\nPasswords, database URLs, tokens, and UUIDs are not retained.",
            8.5F,
            FontStyle.Regular,
            Theme.Muted);
        secureBody.AutoSize = true;
        secureBody.Location = new Point(20, 46);
        security.Controls.Add(secureBody);
    }

    private void StartPageEntrance()
    {
        StopPageEntrance();

        List<Control> controls = new List<Control>();
        foreach (Control control in content.Controls)
        {
            if (control.Visible && control.Dock == DockStyle.None) controls.Add(control);
        }
        controls.Sort(delegate(Control left, Control right)
        {
            int byTop = left.Top.CompareTo(right.Top);
            return byTop != 0 ? byTop : left.Left.CompareTo(right.Left);
        });

        for (int index = 0; index < controls.Count; index++)
        {
            Control control = controls[index];
            pageMotions.Add(new PageMotion(control, control.Left, Math.Min(70, index * 11)));
            control.Left += 16;
        }

        if (pageMotions.Count == 0) return;
        pageEntranceStarted = DateTime.UtcNow;
        pageEntranceTimer.Start();
    }

    private void StopPageEntrance()
    {
        pageEntranceTimer.Stop();
        for (int index = 0; index < pageMotions.Count; index++)
        {
            if (!pageMotions[index].Control.IsDisposed)
            {
                pageMotions[index].Control.Left = pageMotions[index].FinalLeft;
            }
        }
        pageMotions.Clear();
    }

    private void AnimatePageEntrance(object sender, EventArgs eventArgs)
    {
        double elapsed = (DateTime.UtcNow - pageEntranceStarted).TotalMilliseconds;
        bool complete = true;

        for (int index = 0; index < pageMotions.Count; index++)
        {
            PageMotion motion = pageMotions[index];
            double progress = (elapsed - motion.DelayMilliseconds) / 220.0;
            if (progress < 1.0) complete = false;
            float eased = Motion.EaseOutCubic((float)Math.Max(0.0, Math.Min(1.0, progress)));
            motion.Control.Left = motion.FinalLeft + (int)Math.Round(16.0 * (1.0 - eased));
        }

        if (!complete) return;
        pageEntranceTimer.Stop();
        for (int index = 0; index < pageMotions.Count; index++)
        {
            pageMotions[index].Control.Left = pageMotions[index].FinalLeft;
        }
        pageMotions.Clear();
    }

    private Bitmap CaptureContent()
    {
        int width = Math.Max(1, content.ClientSize.Width);
        int height = Math.Max(1, content.ClientSize.Height);
        Bitmap bitmap = new Bitmap(width, height);
        content.DrawToBitmap(bitmap, new Rectangle(0, 0, width, height));
        return bitmap;
    }

    private void StartBusy(string message, Color accent)
    {
        loadingOverlay.BeginBusy(CaptureContent(), message, accent);
    }

    private void FinishBusy(string message, Color accent)
    {
        loadingOverlay.FinishBusy(CaptureContent(), message, accent);
    }

    private void GrantAccount(string role, string rawUuid)
    {
        Guid parsed;
        if (!Guid.TryParse((rawUuid ?? "").Trim(), out parsed))
        {
            SetStatus("Enter a valid Minecraft UUID.", Theme.Danger);
            GDialog.ShowMessage(this, "Invalid UUID", "Enter the player's complete Minecraft UUID.", Theme.Danger);
            return;
        }
        if (!RequireBackend()) return;

        string message = "Create or reset " + role + " access for:\n\n" + parsed.ToString("D")
            + "\n\nExisting moderator sessions for this UUID will be revoked.";
        if (!GDialog.Confirm(this, "Confirm account access", message, Theme.Accent)) return;
        char[] password;
        if (!SecurePasswordDialog.TryCollect(this, role, out password)) return;

        List<string> remote = new List<string>();
        remote.Add("env");
        remote.Add("ADMIN_TARGET_UUID=" + parsed.ToString("D"));
        remote.Add("ADMIN_ROLE=" + role);
        remote.Add("ADMIN_PASSWORD_STDIN=1");
        remote.Add("node");
        remote.Add("/app/dist/admin-cli.js");

        SetStatus("Securely creating the account…", Theme.Accent);
        StartBusy("Creating UUID-bound account…", Theme.Accent);
        AdminRuntime.RunRemoteCapturedAsync(remote, password, delegate(RemoteCommandResult result)
        {
            if (IsDisposed) return;
            BeginInvoke((MethodInvoker)delegate
            {
                if (result.ExitCode == 0)
                {
                    SetStatus(role + " account operation completed.", Theme.Success);
                    FinishBusy("Account access is ready", Theme.Success);
                    GDialog.ShowMessage(
                        this,
                        "Account ready",
                        "The " + role + " account was created or reset.\nThe player must replace the initial password on first login.",
                        Theme.Success);
                }
                else
                {
                    SetStatus("Backend account operation failed.", Theme.Danger);
                    FinishBusy("Account operation failed", Theme.Danger);
                    GDialog.ShowMessage(
                        this,
                        "Backend command failed",
                        result.Message,
                        Theme.Danger);
                }
            });
        });
    }

    private void RevokeAccount(string rawUuid)
    {
        Guid parsed;
        if (!Guid.TryParse((rawUuid ?? "").Trim(), out parsed))
        {
            SetStatus("Enter a valid Minecraft UUID.", Theme.Danger);
            GDialog.ShowMessage(this, "Invalid UUID", "Enter the player's complete Minecraft UUID.", Theme.Danger);
            return;
        }
        if (!RequireBackend()) return;

        if (!GDialog.Confirm(
            this,
            "Revoke admin access",
            "Disable this account and delete every active admin session?\n\n" + parsed.ToString("D"),
            Theme.Danger)) return;

        List<string> remote = new List<string>();
        remote.Add("env");
        remote.Add("ADMIN_ACTION=revoke");
        remote.Add("ADMIN_TARGET_UUID=" + parsed.ToString("D"));
        remote.Add("node");
        remote.Add("/app/dist/admin-cli.js");

        SetStatus("Securely revoking access…", Theme.Danger);
        StartBusy("Revoking access and active sessions…", Theme.Danger);
        AdminRuntime.RunRemoteCapturedAsync(remote, null, delegate(RemoteCommandResult result)
        {
            if (IsDisposed) return;
            BeginInvoke((MethodInvoker)delegate
            {
                if (result.ExitCode == 0)
                {
                    SetStatus("Admin access revoked.", Theme.Success);
                    FinishBusy("Access revoked", Theme.Success);
                    GDialog.ShowMessage(this, "Access revoked", "The account is disabled and its sessions were deleted.", Theme.Success);
                }
                else
                {
                    SetStatus("Revocation failed.", Theme.Danger);
                    FinishBusy("Revocation failed", Theme.Danger);
                    GDialog.ShowMessage(this, "Backend command failed", result.Message, Theme.Danger);
                }
            });
        });
    }

    private bool RequireBackend()
    {
        if (AdminRuntime.GetRailwayVersion() == null)
        {
            GDialog.ShowMessage(
                this,
                "Railway CLI required",
                AdminRuntime.FindRailway() == null
                    ? "Open Backend and install the required tools first."
                    : "The Railway launcher is broken. Open Backend and use Repair Railway CLI.",
                Theme.Danger);
            ShowPage(PageKind.Backend);
            return false;
        }
        if (!AdminRuntime.HasLocalSshKey())
        {
            GDialog.ShowMessage(
                this,
                "SSH key required",
                "Open Backend and select Set up SSH access first.",
                Theme.Danger);
            ShowPage(PageKind.Backend);
            return false;
        }
        if (AdminRuntime.LoadTargetArguments() == null)
        {
            GDialog.ShowMessage(this, "Backend target required", "Open Backend and paste Railway's copied SSH command first.", Theme.Danger);
            ShowPage(PageKind.Backend);
            return false;
        }
        return true;
    }

    private void InstallTools()
    {
        if (!GDialog.Confirm(
            this,
            "Install or repair Railway tools",
            "Install Node.js LTS if needed, then repair/reinstall the official Railway CLI?\n\nProgress and results remain inside G'Network Admin.",
            Theme.Blue)) return;

        SetStatus("Installing or repairing Node.js and Railway CLI…", Theme.Blue);
        StartBusy("Repairing Railway tooling…", Theme.Blue);
        AdminRuntime.InstallToolsAsync(delegate(bool success, string message)
        {
            if (IsDisposed) return;
            BeginInvoke((MethodInvoker)delegate
            {
                SetStatus(message, success ? Theme.Success : Theme.Danger);
                ShowPage(PageKind.Backend, false);
                FinishBusy(
                    success ? "Railway tools are ready" : "Tool setup needs attention",
                    success ? Theme.Success : Theme.Danger);
                GDialog.ShowMessage(this, success ? "Tools installed" : "Installation failed", message, success ? Theme.Success : Theme.Danger);
            });
        });
    }

    private void SetupSshAccess()
    {
        if (AdminRuntime.GetRailwayVersion() == null)
        {
            GDialog.ShowMessage(
                this,
                "Railway CLI required",
                "Install or repair Railway CLI before setting up SSH access.",
                Theme.Danger);
            return;
        }
        if (!GDialog.Confirm(
            this,
            "Set up Railway SSH access",
            "Create or reuse a local SSH key and register its public half with Railway?\n\nThe private key remains under your Windows profile and is never stored by this app or GitHub.",
            Theme.Blue)) return;

        SetStatus("Setting up Railway SSH access…", Theme.Blue);
        StartBusy("Registering secure SSH access…", Theme.Blue);
        AdminRuntime.SetupSshAccessAsync(delegate(bool success, string message)
        {
            if (IsDisposed) return;
            BeginInvoke((MethodInvoker)delegate
            {
                SetStatus(message, success ? Theme.Success : Theme.Danger);
                ShowPage(PageKind.Backend, false);
                FinishBusy(
                    success ? "SSH access is ready" : "SSH setup needs attention",
                    success ? Theme.Success : Theme.Danger);
                GDialog.ShowMessage(
                    this,
                    success ? "SSH access ready" : "SSH setup failed",
                    message,
                    success ? Theme.Success : Theme.Danger);
            });
        });
    }

    private void RunRailwayUtility(string command)
    {
        if (AdminRuntime.GetRailwayVersion() == null)
        {
            GDialog.ShowMessage(
                this,
                "Railway CLI required",
                AdminRuntime.FindRailway() == null
                    ? "Install the required tools first."
                    : "Repair the broken Railway CLI installation first.",
                Theme.Danger);
            return;
        }
        SetStatus("Railway " + command + " started.", Theme.Blue);
        StartBusy(
            command == "login" ? "Waiting for Railway authentication…" : "Checking Railway account…",
            command == "login" ? Theme.Accent : Theme.Blue);
        AdminRuntime.RunRailwayCapturedAsync(new List<string> { command }, delegate(RemoteCommandResult result)
        {
            if (IsDisposed) return;
            BeginInvoke((MethodInvoker)delegate
            {
                SetStatus(
                    result.ExitCode == 0 ? "Railway " + command + " completed." : "Railway " + command + " failed.",
                    result.ExitCode == 0 ? Theme.Success : Theme.Danger);
                ShowPage(PageKind.Backend, false);
                FinishBusy(
                    result.ExitCode == 0 ? "Railway " + command + " completed" : "Railway " + command + " failed",
                    result.ExitCode == 0 ? Theme.Success : Theme.Danger);
                GDialog.ShowMessage(
                    this,
                    result.ExitCode == 0
                        ? command == "login" ? "Railway login complete" : "Railway account"
                        : "Railway command failed",
                    result.Message,
                    result.ExitCode == 0 ? Theme.Success : Theme.Danger);
            });
        });
    }

    private void OpenPrivateShell()
    {
        if (!RequireBackend()) return;
        SetStatus("Opening the in-app private shell…", Theme.Blue);
        using (PrivateShellDialog dialog = new PrivateShellDialog())
        {
            dialog.ShowDialog(this);
        }
        SetStatus("In-app private shell closed.", Theme.Success);
    }

    private void SaveBackendTarget(string pasted)
    {
        List<string> target;
        string error;
        if (!AdminRuntime.TryExtractTargetArguments(pasted, out target, out error))
        {
            SetStatus(error, Theme.Danger);
            GDialog.ShowMessage(this, "Invalid Railway command", error, Theme.Danger);
            return;
        }

        AdminRuntime.SaveTargetArguments(target);
        SetStatus("Production backend target saved.", Theme.Success);
        GDialog.ShowMessage(this, "Backend configured", "The non-secret Railway target was saved locally.", Theme.Success);
        ShowPage(PageKind.Backend);
    }

    private void ForgetTarget()
    {
        if (AdminRuntime.LoadTargetArguments() == null)
        {
            SetStatus("No backend target is currently saved.", Theme.Muted);
            return;
        }
        if (!GDialog.Confirm(this, "Forget backend target", "Remove the saved Railway service target from this computer?", Theme.Danger)) return;
        AdminRuntime.ForgetTarget();
        SetStatus("Saved backend target removed.", Theme.Success);
        ShowPage(PageKind.Backend);
    }

    private void AddPageHeader(string title, string subtitle, Color accent)
    {
        Label heading = LabelOf(title, 20F, FontStyle.Bold, Theme.Text);
        heading.AutoSize = true;
        heading.Location = new Point(30, 25);
        content.Controls.Add(heading);

        Label sub = LabelOf(subtitle, 9.5F, FontStyle.Regular, Theme.Muted);
        sub.AutoSize = true;
        sub.Location = new Point(32, 63);
        content.Controls.Add(sub);

        Panel accentLine = new Panel();
        accentLine.BackColor = accent;
        accentLine.Location = new Point(30, 87);
        accentLine.Size = new Size(46, 3);
        content.Controls.Add(accentLine);
    }

    private static Panel StatusCard(int left, int top, int width, string eyebrow, string value, string description, Color accent)
    {
        Panel card = Card(left, top, width, 130, accent);

        Label e = LabelOf(eyebrow, 7.5F, FontStyle.Bold, Theme.Faint);
        e.AutoSize = true;
        e.Location = new Point(18, 17);
        card.Controls.Add(e);

        Panel dot = new Panel();
        dot.BackColor = accent;
        dot.Size = new Size(8, 8);
        dot.Location = new Point(18, 48);
        card.Controls.Add(dot);

        Label v = LabelOf(value, 13F, FontStyle.Bold, Theme.Text);
        v.AutoSize = true;
        v.Location = new Point(34, 41);
        card.Controls.Add(v);

        Label d = LabelOf(description, 8.2F, FontStyle.Regular, Theme.Muted);
        d.MaximumSize = new Size(width - 36, 0);
        d.AutoSize = true;
        d.Location = new Point(18, 78);
        card.Controls.Add(d);

        return card;
    }

    private static Panel Card(int left, int top, int width, int height, Color accent)
    {
        Panel card = new Panel();
        card.Location = new Point(left, top);
        card.Size = new Size(width, height);
        card.BackColor = Theme.Panel;
        card.Paint += delegate(object sender, PaintEventArgs e)
        {
            e.Graphics.SmoothingMode = SmoothingMode.None;
            using (Pen border = new Pen(Theme.Line))
            {
                e.Graphics.DrawRectangle(border, 0, 0, card.Width - 1, card.Height - 1);
            }
            using (Brush brush = new SolidBrush(accent))
            {
                e.Graphics.FillRectangle(brush, 0, 0, 3, card.Height);
            }
        };
        return card;
    }

    private static Panel SoftPanel(int left, int top, int width, int height, Color accent)
    {
        Panel panel = new Panel();
        panel.Location = new Point(left, top);
        panel.Size = new Size(width, height);
        panel.BackColor = Theme.Surface;
        panel.Paint += delegate(object sender, PaintEventArgs e)
        {
            using (Pen pen = new Pen(Color.FromArgb(100, accent)))
            {
                e.Graphics.DrawRectangle(pen, 0, 0, panel.Width - 1, panel.Height - 1);
            }
        };
        return panel;
    }

    private static void AddCardTitle(Panel card, string title, string subtitle)
    {
        Label heading = LabelOf(title, 12F, FontStyle.Bold, Theme.Text);
        heading.AutoSize = true;
        heading.Location = new Point(22, 18);
        card.Controls.Add(heading);

        Label sub = LabelOf(subtitle, 8.5F, FontStyle.Regular, Theme.Muted);
        sub.MaximumSize = new Size(card.Width - 44, 0);
        sub.AutoSize = true;
        sub.Location = new Point(23, 48);
        card.Controls.Add(sub);
    }

    private static void AddStep(Panel parent, int number, string title, string detail, int top)
    {
        Panel badge = new Panel();
        badge.BackColor = Theme.Surface;
        badge.Location = new Point(22, top);
        badge.Size = new Size(30, 30);
        parent.Controls.Add(badge);

        Label n = LabelOf(number.ToString(), 9F, FontStyle.Bold, Theme.Accent);
        n.TextAlign = ContentAlignment.MiddleCenter;
        n.Dock = DockStyle.Fill;
        badge.Controls.Add(n);

        Label heading = LabelOf(title, 9.2F, FontStyle.Bold, Theme.Text);
        heading.AutoSize = true;
        heading.Location = new Point(65, top - 1);
        parent.Controls.Add(heading);

        Label description = LabelOf(detail, 8.2F, FontStyle.Regular, Theme.Muted);
        description.AutoSize = true;
        description.Location = new Point(65, top + 17);
        parent.Controls.Add(description);
    }

    private static TextBox TextField(int left, int top, int width)
    {
        TextBox field = new TextBox();
        field.Location = new Point(left, top);
        field.Size = new Size(width, 30);
        field.BackColor = Theme.Surface;
        field.ForeColor = Theme.Text;
        field.BorderStyle = BorderStyle.FixedSingle;
        field.Font = Theme.Font(10F, FontStyle.Regular);
        return field;
    }

    private static Label LabelOf(string text, float size, FontStyle style, Color color)
    {
        Label label = new Label();
        label.Text = text;
        label.Font = Theme.Font(size, style);
        label.ForeColor = color;
        label.BackColor = Color.Transparent;
        return label;
    }

    private static Image LoadLogo()
    {
        Stream stream = Assembly.GetExecutingAssembly().GetManifestResourceStream("GNetworkLogo.png");
        if (stream == null) return null;
        using (stream)
        using (Image source = Image.FromStream(stream))
        {
            return new Bitmap(source);
        }
    }

    private void SetStatus(string message, Color color)
    {
        footerStatus.Text = message;
        footerStatus.ForeColor = color == Theme.Danger ? Theme.Danger : Theme.Muted;
        footerDot.BackColor = color;
    }

    private void DragWindow(object sender, MouseEventArgs eventArgs)
    {
        if (eventArgs.Button != MouseButtons.Left) return;
        ReleaseCapture();
        SendMessage(Handle, 0xA1, 0x2, 0);
    }

    private void ToggleMaximize()
    {
        if (!maximized)
        {
            restoredBounds = Bounds;
            Bounds = Screen.FromControl(this).WorkingArea;
            maximized = true;
        }
        else
        {
            Bounds = restoredBounds;
            maximized = false;
        }
    }
}

internal sealed class SiteDocument
{
    public SiteSettingsDocument settings { get; set; }
    public SitePageDocument[] pages { get; set; }
}

internal sealed class SiteSettingsDocument
{
    public string siteName { get; set; }
    public string accent { get; set; }
    public string background { get; set; }
    public string panel { get; set; }
    public string updatedAt { get; set; }
}

internal sealed class SitePageDocument
{
    public string id { get; set; }
    public string slug { get; set; }
    public string title { get; set; }
    public string navLabel { get; set; }
    public string eyebrow { get; set; }
    public string headline { get; set; }
    public string body { get; set; }
    public bool published { get; set; }
    public bool showInNav { get; set; }
    public SiteSectionDocument[] sections { get; set; }
    public SiteButtonDocument[] buttons { get; set; }
    public string updatedAt { get; set; }
}

internal sealed class SiteSectionDocument
{
    public string id { get; set; }
    public string title { get; set; }
    public string body { get; set; }
}

internal sealed class SiteButtonDocument
{
    public string id { get; set; }
    public string label { get; set; }
    public string href { get; set; }
    public string style { get; set; }
}

internal sealed class PageMotion
{
    public readonly Control Control;
    public readonly int FinalLeft;
    public readonly int DelayMilliseconds;

    public PageMotion(Control control, int finalLeft, int delayMilliseconds)
    {
        Control = control;
        FinalLeft = finalLeft;
        DelayMilliseconds = delayMilliseconds;
    }
}

internal static class Motion
{
    public static float Clamp01(float value)
    {
        return Math.Max(0F, Math.Min(1F, value));
    }

    public static float EaseOutCubic(float value)
    {
        float clamped = Clamp01(value);
        float inverse = 1F - clamped;
        return 1F - inverse * inverse * inverse;
    }

    public static float EaseInOutCubic(float value)
    {
        float clamped = Clamp01(value);
        return clamped < 0.5F
            ? 4F * clamped * clamped * clamped
            : 1F - (float)Math.Pow(-2F * clamped + 2F, 3F) / 2F;
    }

    public static Color Lerp(Color from, Color to, float amount)
    {
        float clamped = Clamp01(amount);
        return Color.FromArgb(
            (int)Math.Round(from.A + (to.A - from.A) * clamped),
            (int)Math.Round(from.R + (to.R - from.R) * clamped),
            (int)Math.Round(from.G + (to.G - from.G) * clamped),
            (int)Math.Round(from.B + (to.B - from.B) * clamped));
    }
}

internal enum LoadingPhase
{
    Hidden,
    TransitionFadeIn,
    TransitionHold,
    BusyFadeIn,
    Busy,
    CompletionHold,
    FadeOut
}

internal sealed class LoadingOverlay : Control
{
    private const double FadeMilliseconds = 180.0;
    private const double TransitionHoldMilliseconds = 300.0;
    private const double CompletionHoldMilliseconds = 520.0;

    private readonly System.Windows.Forms.Timer animationTimer = new System.Windows.Forms.Timer();
    private Bitmap snapshot;
    private Func<Bitmap> transitionCommit;
    private LoadingPhase phase = LoadingPhase.Hidden;
    private DateTime phaseStarted;
    private DateTime animationStarted;
    private Color accent = Theme.Accent;
    private string message = "Loading…";
    private float opacity;

    public bool Active
    {
        get { return phase != LoadingPhase.Hidden; }
    }

    public LoadingOverlay()
    {
        BackColor = Theme.Surface;
        Cursor = Cursors.WaitCursor;
        TabStop = false;
        SetStyle(
            ControlStyles.UserPaint |
            ControlStyles.AllPaintingInWmPaint |
            ControlStyles.OptimizedDoubleBuffer |
            ControlStyles.ResizeRedraw,
            true);

        animationTimer.Interval = 15;
        animationTimer.Tick += Animate;
    }

    public void BeginTransition(Bitmap background, string loadingMessage, Color loadingAccent, Func<Bitmap> commit)
    {
        ResetSnapshot(background);
        message = string.IsNullOrWhiteSpace(loadingMessage) ? "Opening…" : loadingMessage;
        accent = loadingAccent;
        transitionCommit = commit;
        opacity = 0F;
        phase = LoadingPhase.TransitionFadeIn;
        phaseStarted = DateTime.UtcNow;
        animationStarted = phaseStarted;
        Visible = true;
        BringToFront();
        animationTimer.Start();
        Invalidate();
    }

    public void BeginBusy(Bitmap background, string loadingMessage, Color loadingAccent)
    {
        ResetSnapshot(background);
        message = string.IsNullOrWhiteSpace(loadingMessage) ? "Working…" : loadingMessage;
        accent = loadingAccent;
        transitionCommit = null;
        opacity = 0F;
        phase = LoadingPhase.BusyFadeIn;
        phaseStarted = DateTime.UtcNow;
        animationStarted = phaseStarted;
        Visible = true;
        BringToFront();
        animationTimer.Start();
        Invalidate();
    }

    public void FinishBusy(Bitmap updatedBackground, string completionMessage, Color completionAccent)
    {
        if (!Active)
        {
            if (updatedBackground != null) updatedBackground.Dispose();
            return;
        }

        ResetSnapshot(updatedBackground);
        message = string.IsNullOrWhiteSpace(completionMessage) ? "Complete" : completionMessage;
        accent = completionAccent;
        opacity = 1F;
        phase = LoadingPhase.CompletionHold;
        phaseStarted = DateTime.UtcNow;
        Invalidate();
    }

    protected override void OnPaint(PaintEventArgs e)
    {
        e.Graphics.SmoothingMode = SmoothingMode.AntiAlias;
        e.Graphics.TextRenderingHint = System.Drawing.Text.TextRenderingHint.ClearTypeGridFit;

        if (snapshot != null)
        {
            e.Graphics.DrawImage(snapshot, new Rectangle(0, 0, ClientSize.Width, ClientSize.Height));
        }
        else
        {
            e.Graphics.Clear(Theme.Backdrop);
        }

        int surfaceAlpha = (int)Math.Round(255F * Motion.Clamp01(opacity));
        using (Brush surface = new SolidBrush(Color.FromArgb(surfaceAlpha, 16, 23, 34)))
        {
            e.Graphics.FillRectangle(surface, ClientRectangle);
        }

        float detailAlpha = Motion.Clamp01((opacity - 0.42F) / 0.58F);
        if (detailAlpha <= 0F) return;

        float centerX = ClientSize.Width / 2F;
        float centerY = ClientSize.Height / 2F + 4F;
        using (Font labelFont = Theme.Font(8F, FontStyle.Bold))
        using (Font messageFont = Theme.Font(12F, FontStyle.Bold))
        using (StringFormat centered = new StringFormat())
        {
            centered.Alignment = StringAlignment.Center;
            centered.LineAlignment = StringAlignment.Center;

            using (Brush labelBrush = new SolidBrush(Color.FromArgb(
                (int)(215F * detailAlpha), accent.R, accent.G, accent.B)))
            {
                e.Graphics.DrawString(
                    phase == LoadingPhase.CompletionHold ? "G'NETWORK  /  COMPLETE" : "G'NETWORK  /  SECURE OPERATION",
                    labelFont,
                    labelBrush,
                    new RectangleF(20F, centerY - 72F, ClientSize.Width - 40F, 22F),
                    centered);
            }

            using (Brush messageBrush = new SolidBrush(Color.FromArgb(
                (int)(255F * detailAlpha), Theme.Text.R, Theme.Text.G, Theme.Text.B)))
            {
                e.Graphics.DrawString(
                    message,
                    messageFont,
                    messageBrush,
                    new RectangleF(30F, centerY - 47F, ClientSize.Width - 60F, 32F),
                    centered);
            }
        }

        if (phase == LoadingPhase.CompletionHold)
        {
            DrawCompletion(e.Graphics, centerX, centerY + 17F, detailAlpha);
        }
        else
        {
            DrawSpinner(e.Graphics, centerX, centerY + 17F, detailAlpha);
        }
    }

    protected override void Dispose(bool disposing)
    {
        if (disposing)
        {
            animationTimer.Stop();
            animationTimer.Dispose();
            if (snapshot != null)
            {
                snapshot.Dispose();
                snapshot = null;
            }
        }
        base.Dispose(disposing);
    }

    private void Animate(object sender, EventArgs eventArgs)
    {
        double elapsed = (DateTime.UtcNow - phaseStarted).TotalMilliseconds;

        if (phase == LoadingPhase.TransitionFadeIn || phase == LoadingPhase.BusyFadeIn)
        {
            opacity = Motion.EaseInOutCubic((float)(elapsed / FadeMilliseconds));
            if (elapsed >= FadeMilliseconds)
            {
                opacity = 1F;
                if (phase == LoadingPhase.TransitionFadeIn)
                {
                    Bitmap committed = transitionCommit == null ? null : transitionCommit();
                    transitionCommit = null;
                    if (committed != null) ResetSnapshot(committed);
                    phase = LoadingPhase.TransitionHold;
                }
                else
                {
                    phase = LoadingPhase.Busy;
                }
                phaseStarted = DateTime.UtcNow;
            }
        }
        else if (phase == LoadingPhase.TransitionHold && elapsed >= TransitionHoldMilliseconds)
        {
            phase = LoadingPhase.FadeOut;
            phaseStarted = DateTime.UtcNow;
        }
        else if (phase == LoadingPhase.CompletionHold && elapsed >= CompletionHoldMilliseconds)
        {
            phase = LoadingPhase.FadeOut;
            phaseStarted = DateTime.UtcNow;
        }
        else if (phase == LoadingPhase.FadeOut)
        {
            opacity = 1F - Motion.EaseInOutCubic((float)(elapsed / FadeMilliseconds));
            if (elapsed >= FadeMilliseconds)
            {
                opacity = 0F;
                phase = LoadingPhase.Hidden;
                Visible = false;
                animationTimer.Stop();
                if (snapshot != null)
                {
                    snapshot.Dispose();
                    snapshot = null;
                }
            }
        }

        Invalidate();
    }

    private void DrawSpinner(Graphics graphics, float centerX, float centerY, float alpha)
    {
        double seconds = (DateTime.UtcNow - animationStarted).TotalSeconds;
        double rotation = seconds * Math.PI * 2.0;
        const int dots = 12;
        const float radius = 17F;

        for (int index = 0; index < dots; index++)
        {
            double angle = rotation + index * Math.PI * 2.0 / dots;
            float x = centerX + (float)Math.Cos(angle) * radius;
            float y = centerY + (float)Math.Sin(angle) * radius;
            float strength = alpha * (0.20F + 0.80F * (index + 1F) / dots);
            float size = index >= dots - 3 ? 4F : 3F;
            using (Brush brush = new SolidBrush(Color.FromArgb(
                (int)(255F * Motion.Clamp01(strength)), accent.R, accent.G, accent.B)))
            {
                graphics.FillEllipse(brush, x - size / 2F, y - size / 2F, size, size);
            }
        }
    }

    private void DrawCompletion(Graphics graphics, float centerX, float centerY, float alpha)
    {
        using (Pen ring = new Pen(Color.FromArgb(
            (int)(150F * alpha), accent.R, accent.G, accent.B), 1.5F))
        using (Pen check = new Pen(Color.FromArgb(
            (int)(255F * alpha), accent.R, accent.G, accent.B), 2.2F))
        {
            graphics.DrawEllipse(ring, centerX - 16F, centerY - 16F, 32F, 32F);
            check.StartCap = LineCap.Round;
            check.EndCap = LineCap.Round;
            graphics.DrawLines(check, new[]
            {
                new PointF(centerX - 8F, centerY),
                new PointF(centerX - 2F, centerY + 6F),
                new PointF(centerX + 9F, centerY - 7F)
            });
        }
    }

    private void ResetSnapshot(Bitmap replacement)
    {
        if (ReferenceEquals(snapshot, replacement)) return;
        if (snapshot != null) snapshot.Dispose();
        snapshot = replacement;
    }
}

internal enum WindowControlKind
{
    Minimize,
    Maximize,
    Close
}

internal sealed class WindowControlButton : Button
{
    private readonly WindowControlKind kind;
    private readonly System.Windows.Forms.Timer hoverTimer = new System.Windows.Forms.Timer();
    private float hoverProgress;
    private float hoverTarget;
    private bool showRestoreIcon;

    public bool ShowRestoreIcon
    {
        get { return showRestoreIcon; }
        set
        {
            showRestoreIcon = value;
            Invalidate();
        }
    }

    public WindowControlButton(WindowControlKind kind)
    {
        this.kind = kind;
        Text = "";
        TabStop = false;
        Cursor = Cursors.Hand;
        FlatStyle = FlatStyle.Flat;
        BackColor = Color.FromArgb(13, 18, 25);
        FlatAppearance.BorderSize = 0;
        FlatAppearance.MouseDownBackColor = Color.Transparent;
        FlatAppearance.MouseOverBackColor = Color.Transparent;
        SetStyle(
            ControlStyles.UserPaint |
            ControlStyles.AllPaintingInWmPaint |
            ControlStyles.OptimizedDoubleBuffer,
            true);

        hoverTimer.Interval = 15;
        hoverTimer.Tick += delegate
        {
            hoverProgress += (hoverTarget - hoverProgress) * 0.24F;
            if (Math.Abs(hoverProgress - hoverTarget) <= 0.012F)
            {
                hoverProgress = hoverTarget;
                hoverTimer.Stop();
            }
            Invalidate();
        };
    }

    protected override void OnMouseEnter(EventArgs e)
    {
        hoverTarget = 1F;
        hoverTimer.Start();
        base.OnMouseEnter(e);
    }

    protected override void OnMouseLeave(EventArgs e)
    {
        hoverTarget = 0F;
        hoverTimer.Start();
        base.OnMouseLeave(e);
    }

    protected override void OnPaint(PaintEventArgs e)
    {
        Color hoverBackground = kind == WindowControlKind.Close
            ? Color.FromArgb(125, 45, 61)
            : Color.FromArgb(26, 33, 43);
        Color background = Motion.Lerp(BackColor, hoverBackground, hoverProgress);

        e.Graphics.Clear(background);
        e.Graphics.SmoothingMode = SmoothingMode.AntiAlias;
        Color glyphColor = Motion.Lerp(Color.FromArgb(157, 168, 181), Theme.Text, hoverProgress);
        float centerX = ClientSize.Width / 2F;
        float centerY = ClientSize.Height / 2F;

        using (Pen pen = new Pen(glyphColor, 1.25F))
        {
            pen.StartCap = LineCap.Square;
            pen.EndCap = LineCap.Square;

            if (kind == WindowControlKind.Minimize)
            {
                e.Graphics.DrawLine(pen, centerX - 5F, centerY + 3.5F, centerX + 5F, centerY + 3.5F);
            }
            else if (kind == WindowControlKind.Close)
            {
                e.Graphics.DrawLine(pen, centerX - 4F, centerY - 4F, centerX + 4F, centerY + 4F);
                e.Graphics.DrawLine(pen, centerX + 4F, centerY - 4F, centerX - 4F, centerY + 4F);
            }
            else if (showRestoreIcon)
            {
                e.Graphics.DrawRectangle(pen, centerX - 3.5F, centerY - 5F, 8F, 7F);
                e.Graphics.DrawRectangle(pen, centerX - 5.5F, centerY - 2.5F, 8F, 7F);
            }
            else
            {
                e.Graphics.DrawRectangle(pen, centerX - 5F, centerY - 4F, 10F, 8F);
            }
        }
    }

    protected override void Dispose(bool disposing)
    {
        if (disposing)
        {
            hoverTimer.Stop();
            hoverTimer.Dispose();
        }
        base.Dispose(disposing);
    }
}

internal sealed class GButton : Button
{
    private readonly System.Windows.Forms.Timer hoverTimer = new System.Windows.Forms.Timer();
    private Color normalColor;
    private Color hoverColor;
    private Color displayedColor;
    private Color targetColor;
    private Color accentColor = Color.Transparent;

    public GButton(string text, Color normal, Color hover)
    {
        Text = text;
        normalColor = normal;
        hoverColor = hover;
        displayedColor = normal;
        targetColor = normal;
        BackColor = displayedColor;
        ForeColor = Theme.Text;
        Font = Theme.Font(9F, FontStyle.Bold);
        FlatStyle = FlatStyle.Flat;
        FlatAppearance.BorderSize = 0;
        Cursor = Cursors.Hand;
        TextAlign = ContentAlignment.MiddleCenter;
        TabStop = true;

        hoverTimer.Interval = 15;
        hoverTimer.Tick += delegate
        {
            displayedColor = Motion.Lerp(displayedColor, targetColor, 0.24F);
            BackColor = displayedColor;
            if (ColorDistance(displayedColor, targetColor) <= 3)
            {
                displayedColor = targetColor;
                BackColor = displayedColor;
                hoverTimer.Stop();
            }
        };
    }

    public Color AccentColor
    {
        get { return accentColor; }
        set
        {
            accentColor = value;
            Invalidate();
        }
    }

    public void SetPalette(Color normal, Color hover, Color foreground)
    {
        normalColor = normal;
        hoverColor = hover;
        ForeColor = foreground;
        targetColor = ClientRectangle.Contains(PointToClient(Cursor.Position)) && Enabled
            ? hoverColor
            : normalColor;
        hoverTimer.Start();
    }

    protected override void OnMouseEnter(EventArgs e)
    {
        base.OnMouseEnter(e);
        if (!Enabled) return;
        targetColor = hoverColor;
        hoverTimer.Start();
    }

    protected override void OnMouseLeave(EventArgs e)
    {
        base.OnMouseLeave(e);
        targetColor = normalColor;
        hoverTimer.Start();
    }

    protected override void OnPaint(PaintEventArgs e)
    {
        base.OnPaint(e);
        if (accentColor != Color.Transparent)
        {
            using (Brush brush = new SolidBrush(accentColor))
            {
                e.Graphics.FillRectangle(brush, 0, 0, 3, Height);
            }
        }
    }

    protected override void Dispose(bool disposing)
    {
        if (disposing)
        {
            hoverTimer.Stop();
            hoverTimer.Dispose();
        }
        base.Dispose(disposing);
    }

    private static int ColorDistance(Color left, Color right)
    {
        return Math.Abs(left.R - right.R)
            + Math.Abs(left.G - right.G)
            + Math.Abs(left.B - right.B)
            + Math.Abs(left.A - right.A);
    }
}

internal sealed class NavButton : Button
{
    private readonly System.Windows.Forms.Timer motionTimer = new System.Windows.Forms.Timer();
    private bool selected;
    private bool hovering;
    private Color displayedBack = Theme.Panel;
    private Color displayedFore = Theme.Muted;
    private float indicatorProgress;

    public NavButton(string text)
    {
        Text = text;
        TextAlign = ContentAlignment.MiddleLeft;
        Padding = new Padding(14, 0, 0, 0);
        FlatStyle = FlatStyle.Flat;
        FlatAppearance.BorderSize = 0;
        FlatAppearance.MouseOverBackColor = Color.Transparent;
        FlatAppearance.MouseDownBackColor = Color.Transparent;
        BackColor = Theme.Panel;
        ForeColor = Theme.Muted;
        Font = Theme.Font(9.5F, FontStyle.Bold);
        Cursor = Cursors.Hand;

        motionTimer.Interval = 15;
        motionTimer.Tick += AnimateState;
    }

    public bool Selected
    {
        get { return selected; }
        set
        {
            selected = value;
            motionTimer.Start();
        }
    }

    protected override void OnMouseEnter(EventArgs e)
    {
        base.OnMouseEnter(e);
        hovering = true;
        motionTimer.Start();
    }

    protected override void OnMouseLeave(EventArgs e)
    {
        base.OnMouseLeave(e);
        hovering = false;
        motionTimer.Start();
    }

    protected override void OnPaint(PaintEventArgs e)
    {
        base.OnPaint(e);
        if (indicatorProgress > 0.01F)
        {
            int height = Math.Max(2, (int)Math.Round(Height * indicatorProgress));
            int top = (Height - height) / 2;
            using (Brush brush = new SolidBrush(Color.FromArgb(
                (int)(255F * indicatorProgress),
                Theme.Accent.R,
                Theme.Accent.G,
                Theme.Accent.B)))
            {
                e.Graphics.FillRectangle(brush, 0, top, 3, height);
            }
        }
    }

    protected override void Dispose(bool disposing)
    {
        if (disposing)
        {
            motionTimer.Stop();
            motionTimer.Dispose();
        }
        base.Dispose(disposing);
    }

    private void AnimateState(object sender, EventArgs eventArgs)
    {
        Color targetBack = selected ? Theme.Surface : hovering ? Theme.SurfaceHover : Theme.Panel;
        Color targetFore = selected
            ? Theme.Accent
            : hovering
                ? Color.FromArgb(190, 199, 210)
                : Theme.Muted;
        float targetIndicator = selected ? 1F : 0F;

        displayedBack = Motion.Lerp(displayedBack, targetBack, 0.22F);
        displayedFore = Motion.Lerp(displayedFore, targetFore, 0.22F);
        indicatorProgress += (targetIndicator - indicatorProgress) * 0.24F;
        BackColor = displayedBack;
        ForeColor = displayedFore;
        Invalidate();

        if (ColorDistance(displayedBack, targetBack) <= 3
            && ColorDistance(displayedFore, targetFore) <= 3
            && Math.Abs(indicatorProgress - targetIndicator) <= 0.012F)
        {
            displayedBack = targetBack;
            displayedFore = targetFore;
            indicatorProgress = targetIndicator;
            BackColor = displayedBack;
            ForeColor = displayedFore;
            motionTimer.Stop();
            Invalidate();
        }
    }

    private static int ColorDistance(Color left, Color right)
    {
        return Math.Abs(left.R - right.R)
            + Math.Abs(left.G - right.G)
            + Math.Abs(left.B - right.B);
    }
}

internal sealed class SecurePasswordDialog : Form
{
    private readonly TextBox password = PasswordField(122);
    private readonly TextBox confirmation = PasswordField(204);
    private readonly Label error = new Label();
    private bool accepted;
    private char[] collectedPassword;

    private SecurePasswordDialog(string role)
    {
        Text = "Set initial password";
        ClientSize = new Size(540, 390);
        FormBorderStyle = FormBorderStyle.None;
        StartPosition = FormStartPosition.CenterParent;
        BackColor = Theme.Panel;
        ShowInTaskbar = false;
        TopMost = true;

        Paint += delegate(object sender, PaintEventArgs e)
        {
            using (Pen pen = new Pen(Theme.Line))
            {
                e.Graphics.DrawRectangle(pen, 0, 0, ClientSize.Width - 1, ClientSize.Height - 1);
            }
            using (Brush brush = new SolidBrush(Theme.Accent))
            {
                e.Graphics.FillRectangle(brush, 0, 0, ClientSize.Width, 4);
            }
        };

        Label heading = DialogLabel("Initial " + role.ToLowerInvariant() + " password", 16F, FontStyle.Bold, Theme.Text);
        heading.Location = new Point(26, 25);
        Controls.Add(heading);

        Label intro = DialogLabel(
            "This password is sent directly to the backend through encrypted SSH input.\nIt is never placed in command arguments, config files, or logs.",
            9F,
            FontStyle.Regular,
            Theme.Muted);
        intro.Location = new Point(27, 62);
        Controls.Add(intro);

        Label firstLabel = DialogLabel("INITIAL PASSWORD", 8F, FontStyle.Bold, Theme.Muted);
        firstLabel.Location = new Point(28, 105);
        Controls.Add(firstLabel);
        Controls.Add(password);

        Label confirmLabel = DialogLabel("CONFIRM PASSWORD", 8F, FontStyle.Bold, Theme.Muted);
        confirmLabel.Location = new Point(28, 187);
        Controls.Add(confirmLabel);
        Controls.Add(confirmation);

        CheckBox reveal = new CheckBox();
        reveal.Text = "Show password";
        reveal.AutoSize = true;
        reveal.Font = Theme.Font(8.5F, FontStyle.Regular);
        reveal.ForeColor = Theme.Muted;
        reveal.BackColor = Theme.Panel;
        reveal.Location = new Point(28, 253);
        reveal.CheckedChanged += delegate
        {
            password.UseSystemPasswordChar = !reveal.Checked;
            confirmation.UseSystemPasswordChar = !reveal.Checked;
        };
        Controls.Add(reveal);

        Label requirement = DialogLabel("12–128 characters", 8F, FontStyle.Regular, Theme.Faint);
        requirement.Location = new Point(397, 255);
        Controls.Add(requirement);

        error.AutoSize = true;
        error.Font = Theme.Font(8.5F, FontStyle.Bold);
        error.ForeColor = Theme.Danger;
        error.BackColor = Theme.Panel;
        error.Location = new Point(28, 287);
        Controls.Add(error);

        GButton cancel = new GButton("Cancel", Theme.Surface, Theme.SurfaceHover);
        cancel.Location = new Point(300, 330);
        cancel.Size = new Size(100, 38);
        cancel.Click += delegate { Close(); };
        Controls.Add(cancel);

        GButton submit = new GButton("Create account", Theme.Accent, Color.FromArgb(255, 216, 125));
        submit.ForeColor = Theme.Backdrop;
        submit.Location = new Point(410, 330);
        submit.Size = new Size(104, 38);
        submit.Click += delegate { AcceptPassword(); };
        Controls.Add(submit);

        password.KeyDown += SubmitOnEnter;
        confirmation.KeyDown += SubmitOnEnter;
        Shown += delegate { password.Focus(); };
    }

    public static bool TryCollect(IWin32Window owner, string role, out char[] password)
    {
        password = null;
        using (SecurePasswordDialog dialog = new SecurePasswordDialog(role))
        {
            dialog.ShowDialog(owner);
            if (!dialog.accepted || dialog.collectedPassword == null) return false;
            password = dialog.collectedPassword;
            dialog.collectedPassword = null;
            return true;
        }
    }

    public static int RenderPreview(string outputPath)
    {
        using (SecurePasswordDialog dialog = new SecurePasswordDialog("OWNER"))
        {
            dialog.Show();
            Application.DoEvents();
            using (Bitmap bitmap = new Bitmap(dialog.Width, dialog.Height))
            {
                dialog.DrawToBitmap(bitmap, new Rectangle(0, 0, bitmap.Width, bitmap.Height));
                bitmap.Save(outputPath, System.Drawing.Imaging.ImageFormat.Png);
            }
            dialog.Close();
        }
        return 0;
    }

    protected override bool ProcessCmdKey(ref Message message, Keys keyData)
    {
        if (keyData == Keys.Escape)
        {
            Close();
            return true;
        }
        return base.ProcessCmdKey(ref message, keyData);
    }

    protected override void Dispose(bool disposing)
    {
        if (disposing && collectedPassword != null)
        {
            Array.Clear(collectedPassword, 0, collectedPassword.Length);
            collectedPassword = null;
        }
        password.Clear();
        confirmation.Clear();
        base.Dispose(disposing);
    }

    private void AcceptPassword()
    {
        string first = password.Text;
        string second = confirmation.Text;
        if (first.Length < 12 || first.Length > 128)
        {
            error.Text = "Use between 12 and 128 characters.";
            password.Focus();
            return;
        }
        if (!string.Equals(first, second, StringComparison.Ordinal))
        {
            error.Text = "The passwords do not match.";
            confirmation.SelectAll();
            confirmation.Focus();
            return;
        }

        collectedPassword = first.ToCharArray();
        accepted = true;
        password.Clear();
        confirmation.Clear();
        Close();
    }

    private void SubmitOnEnter(object sender, KeyEventArgs eventArgs)
    {
        if (eventArgs.KeyCode != Keys.Enter) return;
        eventArgs.Handled = true;
        eventArgs.SuppressKeyPress = true;
        AcceptPassword();
    }

    private static TextBox PasswordField(int top)
    {
        TextBox field = new TextBox();
        field.Location = new Point(28, top);
        field.Size = new Size(484, 34);
        field.Font = Theme.Font(11F, FontStyle.Regular);
        field.ForeColor = Theme.Text;
        field.BackColor = Theme.Surface;
        field.BorderStyle = BorderStyle.FixedSingle;
        field.UseSystemPasswordChar = true;
        field.MaxLength = 128;
        return field;
    }

    private static Label DialogLabel(string text, float size, FontStyle style, Color color)
    {
        Label label = new Label();
        label.Text = text;
        label.Font = Theme.Font(size, style);
        label.ForeColor = color;
        label.BackColor = Theme.Panel;
        label.AutoSize = true;
        return label;
    }
}

internal sealed class PrivateShellDialog : Form
{
    private readonly RichTextBox output = new RichTextBox();
    private readonly TextBox command = new TextBox();
    private readonly GButton send = new GButton("Run command", Theme.Blue, Color.FromArgb(125, 183, 255));
    private readonly Label status = new Label();
    private readonly Panel statusDot = new Panel();
    private RemoteShellSession session;

    public PrivateShellDialog()
    {
        Text = "G'Network in-app private shell";
        ClientSize = new Size(860, 590);
        MinimumSize = new Size(700, 500);
        FormBorderStyle = FormBorderStyle.None;
        StartPosition = FormStartPosition.CenterParent;
        BackColor = Theme.Panel;
        ShowInTaskbar = false;
        KeyPreview = true;

        Panel header = new Panel();
        header.Dock = DockStyle.Top;
        header.Height = 44;
        header.BackColor = Color.FromArgb(13, 18, 25);
        Controls.Add(header);

        Label title = ShellLabel("G'Network  /  Private backend shell", 10.5F, FontStyle.Bold, Theme.Muted);
        title.Location = new Point(18, 13);
        header.Controls.Add(title);

        WindowControlButton close = new WindowControlButton(WindowControlKind.Close);
        close.Size = new Size(44, 44);
        close.Dock = DockStyle.Right;
        close.Click += delegate { Close(); };
        header.Controls.Add(close);

        Label heading = ShellLabel("Production command console", 17F, FontStyle.Bold, Theme.Text);
        heading.Location = new Point(28, 66);
        Controls.Add(heading);

        Label notice = ShellLabel(
            "Commands run inside the configured Railway backend. Nothing is added to command history or written to a local log. Do not paste credentials.",
            8.5F,
            FontStyle.Regular,
            Theme.Muted);
        notice.Location = new Point(30, 99);
        notice.MaximumSize = new Size(800, 0);
        Controls.Add(notice);

        statusDot.Size = new Size(8, 8);
        statusDot.Location = new Point(30, 135);
        statusDot.BackColor = Theme.Accent;
        Controls.Add(statusDot);

        status.AutoSize = true;
        status.Location = new Point(46, 130);
        status.Font = Theme.Font(8.5F, FontStyle.Bold);
        status.ForeColor = Theme.Accent;
        status.BackColor = Theme.Panel;
        status.Text = "Connecting securely…";
        Controls.Add(status);

        output.Location = new Point(28, 158);
        output.Size = new Size(804, 354);
        output.Anchor = AnchorStyles.Top | AnchorStyles.Bottom | AnchorStyles.Left | AnchorStyles.Right;
        output.BackColor = Color.FromArgb(7, 11, 16);
        output.ForeColor = Theme.Text;
        output.BorderStyle = BorderStyle.FixedSingle;
        output.Font = new Font("Consolas", 9.5F, FontStyle.Regular, GraphicsUnit.Point);
        output.ReadOnly = true;
        output.DetectUrls = false;
        output.WordWrap = false;
        output.ScrollBars = RichTextBoxScrollBars.Both;
        Controls.Add(output);

        command.Location = new Point(28, 530);
        command.Size = new Size(622, 34);
        command.Anchor = AnchorStyles.Bottom | AnchorStyles.Left | AnchorStyles.Right;
        command.BackColor = Theme.Surface;
        command.ForeColor = Theme.Text;
        command.BorderStyle = BorderStyle.FixedSingle;
        command.Font = new Font("Consolas", 10.5F, FontStyle.Regular, GraphicsUnit.Point);
        command.Enabled = false;
        command.KeyDown += SubmitOnEnter;
        Controls.Add(command);

        send.Location = new Point(662, 528);
        send.Size = new Size(170, 38);
        send.Anchor = AnchorStyles.Bottom | AnchorStyles.Right;
        send.ForeColor = Theme.Backdrop;
        send.Enabled = false;
        send.Click += delegate { SubmitCommand(); };
        Controls.Add(send);

        Paint += PaintFrame;
        Shown += delegate { StartShell(); };
    }

    protected override void Dispose(bool disposing)
    {
        if (disposing && session != null)
        {
            session.Dispose();
            session = null;
        }
        base.Dispose(disposing);
    }

    private void StartShell()
    {
        AppendOutput("Connecting to the configured production backend…", Theme.Muted);
        string error;
        session = AdminRuntime.StartRemoteShell(AppendSessionOutput, ShellExited, out error);
        if (session == null)
        {
            SetStatus("Connection failed", Theme.Danger);
            AppendOutput(error, Theme.Danger);
            return;
        }
        SetStatus("Connected · enter a command below", Theme.Success);
        command.Enabled = true;
        send.Enabled = true;
        command.Focus();
    }

    private void SubmitCommand()
    {
        string value = command.Text.Trim();
        command.Clear();
        if (value.Length == 0) return;
        if (string.Equals(value, "clear", StringComparison.OrdinalIgnoreCase))
        {
            output.Clear();
            return;
        }

        AppendOutput("> " + value, Theme.Blue);
        if (session == null || !session.Send(value))
        {
            SetStatus("Shell is no longer connected", Theme.Danger);
            command.Enabled = false;
            send.Enabled = false;
            return;
        }
        if (string.Equals(value, "exit", StringComparison.OrdinalIgnoreCase))
        {
            SetStatus("Closing the secure shell…", Theme.Accent);
            command.Enabled = false;
            send.Enabled = false;
        }
    }

    private void SubmitOnEnter(object sender, KeyEventArgs eventArgs)
    {
        if (eventArgs.KeyCode != Keys.Enter) return;
        eventArgs.Handled = true;
        eventArgs.SuppressKeyPress = true;
        SubmitCommand();
    }

    private void AppendSessionOutput(string line, bool isError)
    {
        AppendOutput(line, isError ? Theme.Danger : Theme.Text);
    }

    private void AppendOutput(string text, Color color)
    {
        if (IsDisposed || Disposing) return;
        if (InvokeRequired)
        {
            try
            {
                BeginInvoke((MethodInvoker)delegate { AppendOutput(text, color); });
            }
            catch
            {
            }
            return;
        }

        string cleaned = Regex.Replace(text ?? "", "\x1B\\[[0-?]*[ -/]*[@-~]", "");
        output.SelectionStart = output.TextLength;
        output.SelectionLength = 0;
        output.SelectionColor = color;
        output.AppendText(cleaned + Environment.NewLine);
        output.SelectionColor = output.ForeColor;
        output.SelectionStart = output.TextLength;
        output.ScrollToCaret();
    }

    private void ShellExited(int exitCode)
    {
        if (IsDisposed || Disposing) return;
        try
        {
            BeginInvoke((MethodInvoker)delegate
            {
                command.Enabled = false;
                send.Enabled = false;
                SetStatus(
                    exitCode == 0 ? "Shell session ended" : "Shell connection ended with an error",
                    exitCode == 0 ? Theme.Muted : Theme.Danger);
            });
        }
        catch
        {
        }
    }

    private void SetStatus(string text, Color color)
    {
        status.Text = text;
        status.ForeColor = color;
        statusDot.BackColor = color;
    }

    private void PaintFrame(object sender, PaintEventArgs eventArgs)
    {
        using (Pen pen = new Pen(Theme.Line))
        {
            eventArgs.Graphics.DrawRectangle(pen, 0, 0, ClientSize.Width - 1, ClientSize.Height - 1);
        }
        using (Brush brush = new SolidBrush(Theme.Blue))
        {
            eventArgs.Graphics.FillRectangle(brush, 0, 0, ClientSize.Width, 3);
        }
    }

    private static Label ShellLabel(string text, float size, FontStyle style, Color color)
    {
        Label label = new Label();
        label.Text = text;
        label.Font = Theme.Font(size, style);
        label.ForeColor = color;
        label.BackColor = Theme.Panel;
        label.AutoSize = true;
        return label;
    }
}

internal sealed class GDialog : Form
{
    private bool accepted;

    private GDialog(string title, string message, Color accent, bool confirmation)
    {
        Text = title;
        ClientSize = new Size(500, 242);
        FormBorderStyle = FormBorderStyle.None;
        StartPosition = FormStartPosition.CenterParent;
        BackColor = Theme.Panel;
        ShowInTaskbar = false;
        TopMost = true;

        Paint += delegate(object sender, PaintEventArgs e)
        {
            using (Pen pen = new Pen(Theme.Line))
            {
                e.Graphics.DrawRectangle(pen, 0, 0, ClientSize.Width - 1, ClientSize.Height - 1);
            }
            using (Brush brush = new SolidBrush(accent))
            {
                e.Graphics.FillRectangle(brush, 0, 0, ClientSize.Width, 4);
            }
        };

        Label heading = new Label();
        heading.Text = title;
        heading.Font = Theme.Font(14F, FontStyle.Bold);
        heading.ForeColor = Theme.Text;
        heading.AutoSize = true;
        heading.Location = new Point(24, 24);
        Controls.Add(heading);

        Label body = new Label();
        body.Text = message;
        body.Font = Theme.Font(9.5F, FontStyle.Regular);
        body.ForeColor = Theme.Muted;
        body.MaximumSize = new Size(452, 100);
        body.AutoSize = true;
        body.Location = new Point(25, 64);
        Controls.Add(body);

        if (confirmation)
        {
            GButton cancel = new GButton("Cancel", Theme.Surface, Theme.SurfaceHover);
            cancel.Location = new Point(260, 184);
            cancel.Size = new Size(100, 38);
            cancel.Click += delegate { accepted = false; Close(); };
            Controls.Add(cancel);

            GButton confirm = new GButton("Confirm", accent, ControlPaint.Light(accent, 0.12F));
            confirm.ForeColor = Theme.Backdrop;
            confirm.Location = new Point(370, 184);
            confirm.Size = new Size(104, 38);
            confirm.Click += delegate { accepted = true; Close(); };
            Controls.Add(confirm);
        }
        else
        {
            GButton okay = new GButton("Okay", accent, ControlPaint.Light(accent, 0.12F));
            okay.ForeColor = Theme.Backdrop;
            okay.Location = new Point(370, 184);
            okay.Size = new Size(104, 38);
            okay.Click += delegate { Close(); };
            Controls.Add(okay);
        }
    }

    public static bool Confirm(IWin32Window owner, string title, string message, Color accent)
    {
        using (GDialog dialog = new GDialog(title, message, accent, true))
        {
            dialog.ShowDialog(owner);
            return dialog.accepted;
        }
    }

    public static void ShowMessage(IWin32Window owner, string title, string message, Color accent)
    {
        using (GDialog dialog = new GDialog(title, message, accent, false))
        {
            dialog.ShowDialog(owner);
        }
    }

    protected override bool ProcessCmdKey(ref Message message, Keys keyData)
    {
        if (keyData == Keys.Escape)
        {
            accepted = false;
            Close();
            return true;
        }
        return base.ProcessCmdKey(ref message, keyData);
    }
}

internal sealed class RemoteShellSession : IDisposable
{
    private readonly Process process = new Process();
    private readonly object inputLock = new object();
    private readonly Action<string, bool> output;
    private readonly Action<int> completion;
    private int disposed;

    public RemoteShellSession(
        ProcessStartInfo start,
        Action<string, bool> outputCallback,
        Action<int> completionCallback)
    {
        output = outputCallback;
        completion = completionCallback;
        process.StartInfo = start;
        process.EnableRaisingEvents = true;
        process.OutputDataReceived += delegate(object sender, DataReceivedEventArgs eventArgs)
        {
            if (eventArgs.Data != null && output != null) output(eventArgs.Data, false);
        };
        process.ErrorDataReceived += delegate(object sender, DataReceivedEventArgs eventArgs)
        {
            if (eventArgs.Data != null && output != null) output(eventArgs.Data, true);
        };
        process.Exited += delegate
        {
            int exitCode = 1;
            try { exitCode = process.ExitCode; }
            catch { }
            if (completion != null) completion(exitCode);
        };

        if (!process.Start())
        {
            process.Dispose();
            throw new InvalidOperationException("The Railway shell process could not be started.");
        }
        process.BeginOutputReadLine();
        process.BeginErrorReadLine();
    }

    public bool Send(string command)
    {
        if (Volatile.Read(ref disposed) != 0) return false;
        lock (inputLock)
        {
            try
            {
                if (process.HasExited) return false;
                process.StandardInput.Write(command);
                process.StandardInput.Write("\n");
                process.StandardInput.Flush();
                return true;
            }
            catch
            {
                return false;
            }
        }
    }

    public void Dispose()
    {
        if (Interlocked.Exchange(ref disposed, 1) != 0) return;
        lock (inputLock)
        {
            try
            {
                if (!process.HasExited)
                {
                    process.StandardInput.Write("exit\n");
                    process.StandardInput.Flush();
                    process.StandardInput.Close();
                }
            }
            catch
            {
            }
        }

        ThreadPool.QueueUserWorkItem(delegate
        {
            try
            {
                if (!process.WaitForExit(2000)) process.Kill();
            }
            catch
            {
            }
            finally
            {
                process.Dispose();
            }
        });
    }
}

internal sealed class RemoteCommandResult
{
    public readonly int ExitCode;
    public readonly string Message;

    public RemoteCommandResult(int exitCode, string message)
    {
        ExitCode = exitCode;
        Message = message;
    }
}

internal static class AdminRuntime
{
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

    public static string FindRailway()
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

    public static string GetRailwayVersion()
    {
        try
        {
            EnsureNodeOnProcessPath(FindNpm());
            string railway = FindRailway();
            if (railway == null) return null;

            ProcessStartInfo start = new ProcessStartInfo();
            if (railway.EndsWith(".cmd", StringComparison.OrdinalIgnoreCase)
                || railway.EndsWith(".bat", StringComparison.OrdinalIgnoreCase))
            {
                start.FileName = Environment.GetEnvironmentVariable("ComSpec") ?? "cmd.exe";
                start.Arguments = "/d /s /c \"\"" + railway.Replace("\"", "\"\"") + "\" --version\"";
            }
            else
            {
                start.FileName = railway;
                start.Arguments = "--version";
            }
            start.UseShellExecute = false;
            start.CreateNoWindow = true;
            start.RedirectStandardOutput = true;
            start.RedirectStandardError = true;

            using (Process process = Process.Start(start))
            {
                if (process == null) return null;
                if (!process.WaitForExit(5000))
                {
                    try { process.Kill(); }
                    catch { }
                    return null;
                }

                string output = process.StandardOutput.ReadToEnd().Trim();
                if (process.ExitCode != 0 || output.Length == 0) return null;
                string[] lines = output.Split(new[] { '\r', '\n' }, StringSplitOptions.RemoveEmptyEntries);
                return lines.Length == 0 ? null : lines[0].Trim();
            }
        }
        catch
        {
            return null;
        }
    }

    public static bool HasLocalSshKey()
    {
        return FindLocalSshPrivateKey() != null;
    }

    public static string FindLocalSshPrivateKey()
    {
        try
        {
            string sshDirectory = Path.Combine(
                Environment.GetFolderPath(Environment.SpecialFolder.UserProfile),
                ".ssh");
            if (!Directory.Exists(sshDirectory)) return null;

            string[] preferred =
            {
                "gnetwork_admin_ed25519",
                "id_ed25519",
                "id_ecdsa",
                "id_rsa"
            };
            for (int index = 0; index < preferred.Length; index++)
            {
                string privateKey = Path.Combine(sshDirectory, preferred[index]);
                if (File.Exists(privateKey) && File.Exists(privateKey + ".pub")) return privateKey;
            }

            string[] publicKeys = Directory.GetFiles(sshDirectory, "*.pub", SearchOption.TopDirectoryOnly);
            for (int index = 0; index < publicKeys.Length; index++)
            {
                string privateKey = publicKeys[index].Substring(0, publicKeys[index].Length - 4);
                if (File.Exists(privateKey)) return privateKey;
            }
        }
        catch
        {
        }
        return null;
    }

    public static void AppendSshIdentity(List<string> arguments)
    {
        string identity = FindLocalSshPrivateKey();
        if (identity == null) return;
        arguments.Add("--identity-file");
        arguments.Add(identity);
    }

    public static List<string> LoadTargetArguments()
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

    public static void SaveTargetArguments(List<string> target)
    {
        Directory.CreateDirectory(ConfigDirectory);
        File.WriteAllLines(TargetFile, target.ToArray(), new UTF8Encoding(false));
    }

    public static void ForgetTarget()
    {
        if (File.Exists(TargetFile)) File.Delete(TargetFile);
    }

    public static bool TryExtractTargetArguments(string command, out List<string> target, out string error)
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
            error = "Only a command beginning with “railway ssh” is accepted.";
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
                error = "The SSH command contains an unexpected argument. Copy it directly from Railway again.";
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

    public static RemoteShellSession StartRemoteShell(
        Action<string, bool> output,
        Action<int> completion,
        out string error)
    {
        error = null;
        List<string> target = LoadTargetArguments();
        if (target == null)
        {
            error = "The Railway backend target is not configured.";
            return null;
        }

        try
        {
            EnsureNodeOnProcessPath(FindNpm());
            string railway = FindRailway();
            if (railway == null)
            {
                error = "Railway CLI could not be found.";
                return null;
            }

            List<string> arguments = new List<string>();
            arguments.Add("ssh");
            arguments.AddRange(target);
            AppendSshIdentity(arguments);
            arguments.Add("--");
            arguments.Add("sh");
            return new RemoteShellSession(
                CreateCapturedProcessStartInfo(railway, arguments),
                output,
                completion);
        }
        catch (Exception exception)
        {
            error = "The private shell could not start: " + exception.Message;
            return null;
        }
    }

    public static void RunRemoteCapturedAsync(
        List<string> remoteArguments,
        char[] password,
        Action<RemoteCommandResult> completion)
    {
        List<string> target = LoadTargetArguments();
        if (target == null)
        {
            if (password != null) Array.Clear(password, 0, password.Length);
            completion(new RemoteCommandResult(2, "The Railway backend target is not configured."));
            return;
        }

        List<string> arguments = new List<string>();
        arguments.Add("ssh");
        arguments.AddRange(target);
        AppendSshIdentity(arguments);
        arguments.Add("--");
        arguments.AddRange(remoteArguments);

        ThreadPool.QueueUserWorkItem(delegate
        {
            RemoteCommandResult result;
            try
            {
                EnsureNodeOnProcessPath(FindNpm());
                string railway = FindRailway();
                result = railway == null
                    ? new RemoteCommandResult(9009, "Railway CLI could not be found.")
                    : RunCapturedProcess(railway, arguments, password);
            }
            catch (Exception exception)
            {
                result = new RemoteCommandResult(1, "The backend command could not start: " + exception.Message);
            }
            finally
            {
                if (password != null) Array.Clear(password, 0, password.Length);
            }
            completion(result);
        });
    }

    public static void RunRemoteLongCapturedAsync(
        List<string> remoteArguments,
        Action<RemoteCommandResult> completion)
    {
        List<string> target = LoadTargetArguments();
        if (target == null)
        {
            completion(new RemoteCommandResult(2, "The Railway backend target is not configured."));
            return;
        }

        List<string> arguments = new List<string>();
        arguments.Add("ssh");
        arguments.AddRange(target);
        AppendSshIdentity(arguments);
        arguments.Add("--");
        arguments.AddRange(remoteArguments);

        ThreadPool.QueueUserWorkItem(delegate
        {
            RemoteCommandResult result;
            try
            {
                EnsureNodeOnProcessPath(FindNpm());
                string railway = FindRailway();
                result = railway == null
                    ? new RemoteCommandResult(9009, "Railway CLI could not be found.")
                    : RunCapturedProcess(railway, arguments, null, 16000);
            }
            catch (Exception exception)
            {
                result = new RemoteCommandResult(1, "The audit command could not start: " + exception.Message);
            }
            completion(result);
        });
    }

    public static void RunRemoteWebsiteCapturedAsync(
        List<string> remoteArguments,
        string standardInput,
        Action<RemoteCommandResult> completion)
    {
        List<string> target = LoadTargetArguments();
        if (target == null)
        {
            completion(new RemoteCommandResult(2, "The Railway backend target is not configured."));
            return;
        }

        List<string> arguments = new List<string>();
        arguments.Add("ssh");
        arguments.AddRange(target);
        AppendSshIdentity(arguments);
        arguments.Add("--");
        arguments.AddRange(remoteArguments);

        ThreadPool.QueueUserWorkItem(delegate
        {
            RemoteCommandResult result;
            try
            {
                EnsureNodeOnProcessPath(FindNpm());
                string railway = FindRailway();
                result = railway == null
                    ? new RemoteCommandResult(9009, "Railway CLI could not be found.")
                    : RunCapturedProcess(railway, arguments, null, standardInput, 512 * 1024);
            }
            catch (Exception exception)
            {
                result = new RemoteCommandResult(1, "The website backend command could not start: " + exception.Message);
            }
            completion(result);
        });
    }

    public static void RunRailwayCapturedAsync(
        List<string> arguments,
        Action<RemoteCommandResult> completion)
    {
        ThreadPool.QueueUserWorkItem(delegate
        {
            RemoteCommandResult result;
            try
            {
                EnsureNodeOnProcessPath(FindNpm());
                string railway = FindRailway();
                result = railway == null
                    ? new RemoteCommandResult(9009, "Railway CLI could not be found.")
                    : RunCapturedProcess(railway, arguments, null);
            }
            catch (Exception exception)
            {
                result = new RemoteCommandResult(1, "The Railway command could not start: " + exception.Message);
            }
            completion(result);
        });
    }

    public static void InstallToolsAsync(Action<bool, string> completion)
    {
        ThreadPool.QueueUserWorkItem(delegate
        {
            try
            {
                string npm = FindNpm();
                if (npm == null)
                {
                    RemoteCommandResult nodeResult = RunCapturedProcess(
                        "winget.exe",
                        new List<string>
                        {
                            "install",
                            "--id", "OpenJS.NodeJS.LTS",
                            "--exact",
                            "--accept-package-agreements",
                            "--accept-source-agreements"
                        },
                        null);
                    if (nodeResult.ExitCode != 0)
                    {
                        completion(false, "Node.js installation failed: " + nodeResult.Message);
                        return;
                    }
                    npm = FindNpm();
                }

                if (npm == null)
                {
                    completion(false, "Node.js installed, but npm was not found. Restart Windows and try again.");
                    return;
                }
                EnsureNodeOnProcessPath(npm);
                RemoteCommandResult railwayResult = RunCapturedProcess(
                    npm,
                    new List<string> { "install", "-g", "@railway/cli@latest", "--force" },
                    null);
                string railwayVersion = GetRailwayVersion();
                if (railwayResult.ExitCode != 0 || railwayVersion == null)
                {
                    completion(
                        false,
                        "Railway CLI repair did not complete: " + railwayResult.Message);
                    return;
                }
                completion(true, "Node.js and " + railwayVersion + " are ready.");
            }
            catch (Exception exception)
            {
                completion(false, "Installation failed: " + exception.Message);
            }
        });
    }

    public static void SetupSshAccessAsync(Action<bool, string> completion)
    {
        ThreadPool.QueueUserWorkItem(delegate
        {
            try
            {
                EnsureNodeOnProcessPath(FindNpm());
                string railway = FindRailway();
                if (railway == null || GetRailwayVersion() == null)
                {
                    completion(false, "Railway CLI must be repaired before SSH access can be configured.");
                    return;
                }

                string privateKey = FindLocalSshPrivateKey();
                if (privateKey == null)
                {
                    string sshDirectory = Path.Combine(
                        Environment.GetFolderPath(Environment.SpecialFolder.UserProfile),
                        ".ssh");
                    Directory.CreateDirectory(sshDirectory);
                    privateKey = Path.Combine(sshDirectory, "gnetwork_admin_ed25519");
                    if (File.Exists(privateKey) || File.Exists(privateKey + ".pub"))
                    {
                        completion(false, "The dedicated SSH key path is incomplete. Review the .ssh folder before retrying.");
                        return;
                    }

                    string keygen = Path.Combine(
                        Environment.GetFolderPath(Environment.SpecialFolder.System),
                        "OpenSSH",
                        "ssh-keygen.exe");
                    if (!File.Exists(keygen))
                    {
                        completion(false, "Windows OpenSSH is required, but ssh-keygen.exe was not found.");
                        return;
                    }

                    RemoteCommandResult keyResult = RunCapturedProcess(
                        keygen,
                        new List<string>
                        {
                            "-q",
                            "-t", "ed25519",
                            "-f", privateKey,
                            "-N", "",
                            "-C", "gnetwork-admin-console@" + Environment.MachineName
                        },
                        null);
                    if (keyResult.ExitCode != 0 || !File.Exists(privateKey) || !File.Exists(privateKey + ".pub"))
                    {
                        completion(false, "The local SSH key could not be generated: " + keyResult.Message);
                        return;
                    }
                }

                RemoteCommandResult registerResult = RunCapturedProcess(
                    railway,
                    new List<string>
                    {
                        "ssh", "keys", "add",
                        "--key", privateKey + ".pub",
                        "--name", "GNetwork Admin Console - " + Environment.MachineName
                    },
                    null);
                if (registerResult.ExitCode != 0)
                {
                    completion(false, "Railway did not accept the SSH public key: " + registerResult.Message);
                    return;
                }

                completion(
                    true,
                    "Railway SSH access is ready. If the first connection asks to trust ssh.railway.com, type yes.");
            }
            catch (Exception exception)
            {
                completion(false, "SSH setup failed: " + exception.Message);
            }
        });
    }

    public static int SelfTest()
    {
        try
        {
            List<string> target;
            string error;
            bool accepted = TryExtractTargetArguments(
                "railway ssh -p 11111111-1111-1111-1111-111111111111 -s backend -e production",
                out target,
                out error);
            if (!accepted || target == null || target.Count != 6) return 1;

            bool rejected = !TryExtractTargetArguments(
                "railway ssh -s backend -- sh -c malicious",
                out target,
                out error);
            if (!rejected) return 1;

            Guid uuid;
            if (!Guid.TryParse("12345678-1234-1234-1234-123456789abc", out uuid)) return 1;
            if (!GameServerRuntime.SelfTest()) return 1;
            return 0;
        }
        catch
        {
            return 1;
        }
    }

    public static int BackendShellSelfTest()
    {
        const string Marker = "GNETWORK_EMBEDDED_SHELL_READY";
        StringBuilder received = new StringBuilder();
        object outputLock = new object();
        int exitCode = -1;
        using (ManualResetEvent completed = new ManualResetEvent(false))
        {
            string error;
            using (RemoteShellSession session = StartRemoteShell(
                delegate(string line, bool isError)
                {
                    lock (outputLock) received.AppendLine(line);
                },
                delegate(int code)
                {
                    exitCode = code;
                    completed.Set();
                },
                out error))
            {
                if (session == null) return 1;
                if (!session.Send("printf '" + Marker + "\\n'")) return 1;
                if (!session.Send("exit")) return 1;
                if (!completed.WaitOne(60000)) return 1;
            }
        }

        lock (outputLock)
        {
            return exitCode == 0
                && received.ToString().IndexOf(Marker, StringComparison.Ordinal) >= 0
                ? 0
                : 1;
        }
    }

    private static RemoteCommandResult RunCapturedProcess(
        string executable,
        List<string> arguments,
        char[] password)
    {
        return RunCapturedProcess(executable, arguments, password, null, 420);
    }

    private static RemoteCommandResult RunCapturedProcess(
        string executable,
        List<string> arguments,
        char[] password,
        int maxMessageLength)
    {
        return RunCapturedProcess(executable, arguments, password, null, maxMessageLength);
    }

    private static RemoteCommandResult RunCapturedProcess(
        string executable,
        List<string> arguments,
        char[] password,
        string standardInput,
        int maxMessageLength)
    {
        ProcessStartInfo start = CreateCapturedProcessStartInfo(executable, arguments);

        StringBuilder standardOutput = new StringBuilder();
        StringBuilder standardError = new StringBuilder();
        object outputLock = new object();
        int captureLimit = Math.Max(16000, Math.Min(1024 * 1024, maxMessageLength + 4096));

        using (Process process = new Process())
        {
            process.StartInfo = start;
            process.OutputDataReceived += delegate(object sender, DataReceivedEventArgs eventArgs)
            {
                if (eventArgs.Data == null) return;
                lock (outputLock)
                {
                    if (standardOutput.Length < captureLimit) standardOutput.AppendLine(eventArgs.Data);
                }
            };
            process.ErrorDataReceived += delegate(object sender, DataReceivedEventArgs eventArgs)
            {
                if (eventArgs.Data == null) return;
                lock (outputLock)
                {
                    if (standardError.Length < captureLimit) standardError.AppendLine(eventArgs.Data);
                }
            };

            if (!process.Start())
            {
                return new RemoteCommandResult(1, "The Railway backend command could not be started.");
            }
            process.BeginOutputReadLine();
            process.BeginErrorReadLine();

            try
            {
                if (password != null)
                {
                    process.StandardInput.Write(password, 0, password.Length);
                    process.StandardInput.WriteLine();
                    process.StandardInput.Write(password, 0, password.Length);
                    process.StandardInput.WriteLine();
                    process.StandardInput.Flush();
                }
                else if (standardInput != null)
                {
                    process.StandardInput.Write(standardInput);
                    process.StandardInput.Flush();
                }
            }
            finally
            {
                process.StandardInput.Close();
            }

            if (!process.WaitForExit(180000))
            {
                try { process.Kill(); }
                catch { }
                return new RemoteCommandResult(124, "The backend operation timed out after three minutes.");
            }
            process.WaitForExit();

            string output;
            string error;
            lock (outputLock)
            {
                output = standardOutput.ToString();
                error = standardError.ToString();
            }
            return new RemoteCommandResult(
                process.ExitCode,
                BuildCapturedMessage(process.ExitCode, output, error, maxMessageLength));
        }
    }

    internal static ProcessStartInfo CreateCapturedProcessStartInfo(
        string executable,
        List<string> arguments)
    {
        ProcessStartInfo start = new ProcessStartInfo();
        if (executable.EndsWith(".cmd", StringComparison.OrdinalIgnoreCase)
            || executable.EndsWith(".bat", StringComparison.OrdinalIgnoreCase))
        {
            start.FileName = Environment.GetEnvironmentVariable("ComSpec") ?? "cmd.exe";
            start.Arguments = "/d /s /c \"\"" + executable.Replace("\"", "\"\"") + "\" "
                + JoinArguments(arguments) + "\"";
        }
        else
        {
            start.FileName = executable;
            start.Arguments = JoinArguments(arguments);
        }
        start.UseShellExecute = false;
        start.CreateNoWindow = true;
        start.RedirectStandardInput = true;
        start.RedirectStandardOutput = true;
        start.RedirectStandardError = true;
        start.WorkingDirectory = Environment.GetFolderPath(Environment.SpecialFolder.DesktopDirectory);
        return start;
    }

    private static string BuildCapturedMessage(int exitCode, string output, string error, int maxMessageLength)
    {
        if (exitCode == 0)
        {
            string success = StripAnsi(string.IsNullOrWhiteSpace(output) ? error : output).Trim();
            return success.Length == 0
                ? "The operation completed successfully."
                : LimitMessage(success, maxMessageLength);
        }

        string combined = StripAnsi((error ?? "") + "\n" + (output ?? "")).Trim();
        if (combined.Length == 0)
        {
            return "The Railway backend command exited with code " + exitCode + ".";
        }

        string[] lines = combined.Split(new[] { '\r', '\n' }, StringSplitOptions.RemoveEmptyEntries);
        for (int index = lines.Length - 1; index >= 0; index--)
        {
            string line = lines[index].Trim();
            int errorMarker = line.IndexOf("Error:", StringComparison.OrdinalIgnoreCase);
            if (errorMarker < 0) continue;
            string detail = line.Substring(errorMarker + "Error:".Length).Trim();
            if (detail.Length > 0) return LimitMessage(detail, maxMessageLength);
        }

        List<string> useful = new List<string>();
        for (int index = 0; index < lines.Length; index++)
        {
            string line = lines[index].Trim();
            if (line.Length == 0 || line.StartsWith("at ", StringComparison.Ordinal)) continue;
            useful.Add(line);
        }
        string message = useful.Count == 0
            ? "The Railway backend command exited with code " + exitCode + "."
            : string.Join("\n", useful.ToArray());
        return LimitMessage(message, maxMessageLength);
    }

    private static string StripAnsi(string value)
    {
        return Regex.Replace(value ?? "", @"\x1B\[[0-?]*[ -/]*[@-~]", "");
    }

    private static string LimitMessage(string value, int maximumLength)
    {
        string trimmed = (value ?? "").Trim();
        int limit = Math.Max(80, maximumLength);
        return trimmed.Length <= limit ? trimmed : trimmed.Substring(0, limit - 3) + "…";
    }

    private static string FindNpm()
    {
        string located = FindOnPath("npm.cmd");
        if (located != null) return located;
        string programFiles = Environment.GetFolderPath(Environment.SpecialFolder.ProgramFiles);
        string candidate = Path.Combine(programFiles, "nodejs", "npm.cmd");
        return File.Exists(candidate) ? candidate : null;
    }

    private static void EnsureNodeOnProcessPath(string npm)
    {
        if (string.IsNullOrWhiteSpace(npm)) return;
        string nodeDirectory;
        try { nodeDirectory = Path.GetDirectoryName(npm); }
        catch { return; }
        if (string.IsNullOrWhiteSpace(nodeDirectory)) return;

        string current = Environment.GetEnvironmentVariable("PATH") ?? "";
        string[] entries = current.Split(Path.PathSeparator);
        for (int index = 0; index < entries.Length; index++)
        {
            if (string.Equals(
                entries[index].Trim().Trim('"').TrimEnd('\\'),
                nodeDirectory.TrimEnd('\\'),
                StringComparison.OrdinalIgnoreCase))
            {
                return;
            }
        }
        Environment.SetEnvironmentVariable(
            "PATH",
            nodeDirectory + Path.PathSeparator + current,
            EnvironmentVariableTarget.Process);
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

    private static string[] SplitCommandLine(string commandLine)
    {
        int count;
        IntPtr pointer = CommandLineToArgvW(commandLine, out count);
        if (pointer == IntPtr.Zero) throw new InvalidOperationException("Windows rejected the command line.");
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
}
