using System;
using System.Collections.Generic;
using System.Drawing;
using System.IO;
using System.Linq;
using System.Net;
using System.Security.Cryptography;
using System.Text;
using System.Text.RegularExpressions;
using System.Threading;
using System.Web.Script.Serialization;
using System.Windows.Forms;

internal sealed partial class MainWindow
{
    private ShopDocument shopDocument;
    private TextBox shopTitle;
    private TextBox shopSubtitle;
    private TextBox shopAccent;
    private TextBox shopPanel;
    private ComboBox shopStyle;
    private NumericUpDown shopOfferCount;
    private NumericUpDown shopRotationHours;
    private CheckBox shopCasesEnabled;
    private CheckBox shopOffersEnabled;
    private DataGridView shopSkins;
    private RichTextBox developerJson;

    private Color NavigationAccent(PageKind page)
    {
        if (page == PageKind.Revoke) return Theme.Danger;
        if (page == PageKind.Audit || page == PageKind.Developer) return Theme.Purple;
        if (page == PageKind.Website) return Theme.Success;
        if (page == PageKind.Backend || page == PageKind.GameServers) return Theme.Blue;
        if (page == PageKind.Shop) return Theme.Accent;
        return Theme.Accent;
    }

    private string NavigationName(PageKind page)
    {
        if (page == PageKind.Accounts) return "Accounts";
        if (page == PageKind.Revoke) return "Revoke access";
        if (page == PageKind.Audit) return "Audit history";
        if (page == PageKind.Website) return "Website";
        if (page == PageKind.Backend) return "Backend";
        if (page == PageKind.GameServers) return "Game servers";
        if (page == PageKind.Shop) return "Skin shop";
        if (page == PageKind.Developer) return "Developer";
        return "Overview";
    }

    private void RenderGameServers()
    {
        AddPageHeader("G'Express game servers", "Choose exactly which live server this console controls", Theme.Blue);
        List<GameServerProfile> profiles = GameServerRuntime.LoadProfiles();
        GameServerProfile active = GameServerRuntime.ActiveProfile();

        Panel selector = Card(30, 102, 780, 116, Theme.Blue);
        content.Controls.Add(selector);
        AddCardTitle(selector, "Server profiles", "The selected profile is used by Skin shop and Developer.");

        ComboBox profileList = new ComboBox();
        profileList.Location = new Point(24, 76);
        profileList.Size = new Size(430, 28);
        profileList.DropDownStyle = ComboBoxStyle.DropDownList;
        profileList.BackColor = Theme.Surface;
        profileList.ForeColor = Theme.Text;
        profileList.Font = Theme.Font(9.5F, FontStyle.Regular);
        for (int index = 0; index < profiles.Count; index++) profileList.Items.Add(profiles[index].Name);
        if (active != null) profileList.SelectedItem = active.Name;
        if (profileList.SelectedIndex < 0 && profileList.Items.Count > 0) profileList.SelectedIndex = 0;
        selector.Controls.Add(profileList);

        GButton use = new GButton("Use selected", Theme.Blue, Color.FromArgb(125, 183, 255));
        use.Location = new Point(472, 72);
        use.Size = new Size(132, 34);
        selector.Controls.Add(use);

        GButton test = new GButton("Test connection", Theme.Surface, Theme.SurfaceHover);
        test.Location = new Point(614, 72);
        test.Size = new Size(142, 34);
        selector.Controls.Add(test);

        Panel editor = Card(30, 238, 780, 300, Theme.Blue);
        content.Controls.Add(editor);
        AddCardTitle(editor, "Add or update a server", "Tokens are encrypted for this Windows user before they are saved.");

        Label nameLabel = LabelOf("PROFILE NAME", 8F, FontStyle.Bold, Theme.Muted);
        nameLabel.AutoSize = true;
        nameLabel.Location = new Point(24, 86);
        editor.Controls.Add(nameLabel);
        TextBox name = TextField(24, 108, 220);
        editor.Controls.Add(name);

        Label addressLabel = LabelOf("SERVER ADDRESS + ALLOCATED PORT", 8F, FontStyle.Bold, Theme.Muted);
        addressLabel.AutoSize = true;
        addressLabel.Location = new Point(264, 86);
        editor.Controls.Add(addressLabel);
        TextBox address = TextField(264, 108, 492);
        address.Text = "server-address:30123";
        editor.Controls.Add(address);

        Label tokenLabel = LabelOf("OWNER TOKEN", 8F, FontStyle.Bold, Theme.Muted);
        tokenLabel.AutoSize = true;
        tokenLabel.Location = new Point(24, 158);
        editor.Controls.Add(tokenLabel);
        TextBox token = TextField(24, 180, 732);
        token.UseSystemPasswordChar = true;
        editor.Controls.Add(token);

        CheckBox reveal = ConsoleCheckBox("Show token", 24, 218);
        reveal.CheckedChanged += delegate { token.UseSystemPasswordChar = !reveal.Checked; };
        editor.Controls.Add(reveal);

        GButton save = new GButton("Save profile", Theme.Blue, Color.FromArgb(125, 183, 255));
        save.Location = new Point(470, 238);
        save.Size = new Size(138, 38);
        editor.Controls.Add(save);

        GButton remove = new GButton("Delete profile", Theme.Surface, Theme.SurfaceHover);
        remove.Location = new Point(618, 238);
        remove.Size = new Size(138, 38);
        editor.Controls.Add(remove);

        Action loadSelection = delegate
        {
            GameServerProfile selected = GameServerRuntime.Find(profileList.SelectedItem as string);
            if (selected == null) return;
            name.Text = selected.Name;
            address.Text = selected.Address;
            token.Text = selected.Token;
        };
        profileList.SelectedIndexChanged += delegate { loadSelection(); };
        loadSelection();

        use.Click += delegate
        {
            GameServerProfile selected = GameServerRuntime.Find(profileList.SelectedItem as string);
            if (selected == null) return;
            GameServerRuntime.SetActive(selected.Name);
            SetStatus("Selected game server: " + selected.Name + ".", Theme.Blue);
        };
        test.Click += delegate
        {
            GameServerProfile selected = GameServerRuntime.Find(profileList.SelectedItem as string);
            if (selected == null)
            {
                GDialog.ShowMessage(this, "Choose a server", "Save and select a game-server profile first.", Theme.Danger);
                return;
            }
            TestGameServer(selected);
        };
        save.Click += delegate
        {
            string error;
            GameServerProfile profile;
            if (!GameServerRuntime.TryCreate(name.Text, address.Text, token.Text, out profile, out error))
            {
                GDialog.ShowMessage(this, "Check server profile", error, Theme.Danger);
                return;
            }
            GameServerRuntime.SaveProfile(profile);
            SetStatus("Saved and selected game server: " + profile.Name + ".", Theme.Success);
            ShowPage(PageKind.GameServers, false);
        };
        remove.Click += delegate
        {
            string selected = profileList.SelectedItem as string;
            if (selected == null) return;
            if (!GDialog.Confirm(this, "Delete server profile", "Forget “" + selected + "” on this computer?", Theme.Danger)) return;
            GameServerRuntime.DeleteProfile(selected);
            SetStatus("Game-server profile removed.", Theme.Success);
            ShowPage(PageKind.GameServers, false);
        };

        Panel note = SoftPanel(30, 558, 780, 104, Theme.Purple);
        content.Controls.Add(note);
        Label noteText = LabelOf(
            "DIRECT SERVER SETUP\nEnter the Wepwawet server IP or hostname followed by its allocated port. " +
            "Remote direct connections are encrypted end-to-end with the owner token, so no domain or reverse proxy is required.",
            8.7F, FontStyle.Regular, Theme.Muted);
        noteText.MaximumSize = new Size(730, 0);
        noteText.AutoSize = true;
        noteText.Location = new Point(22, 18);
        note.Controls.Add(noteText);
    }

    private void TestGameServer(GameServerProfile profile)
    {
        SetStatus("Connecting to " + profile.Name + "…", Theme.Blue);
        StartBusy("Testing game-server connection…", Theme.Blue);
        GameServerRuntime.SendAsync(profile, "GET", "/v1/status", null, delegate(GameServerResult result)
        {
            if (IsDisposed) return;
            BeginInvoke((MethodInvoker)delegate
            {
                if (!result.Success)
                {
                    SetStatus("Game-server connection failed.", Theme.Danger);
                    FinishBusy("Connection failed", Theme.Danger);
                    GDialog.ShowMessage(this, "Could not connect", result.Message, Theme.Danger);
                    return;
                }
                SetStatus(profile.Name + " is online and authenticated.", Theme.Success);
                FinishBusy("Game server connected", Theme.Success);
                GDialog.ShowMessage(this, "Server connected", result.Body, Theme.Success);
            });
        });
    }

    private void RenderShop()
    {
        AddPageHeader("Skin shop", "Live presentation, rotation, eligibility, and prices", Theme.Accent);
        GameServerProfile active = GameServerRuntime.ActiveProfile();

        GButton load = new GButton("Load live shop", Theme.Accent, Color.FromArgb(255, 216, 125));
        load.ForeColor = Theme.Backdrop;
        load.Location = new Point(30, 94);
        load.Size = new Size(144, 38);
        content.Controls.Add(load);

        GButton save = new GButton("Save & update live", Theme.Surface, Theme.SurfaceHover);
        save.Location = new Point(184, 94);
        save.Size = new Size(164, 38);
        content.Controls.Add(save);

        Label target = LabelOf(active == null ? "NO GAME SERVER SELECTED" : "TARGET  /  " + active.Name,
            8F, FontStyle.Bold, active == null ? Theme.Danger : Theme.Blue);
        target.AutoSize = true;
        target.Location = new Point(366, 106);
        content.Controls.Add(target);

        Panel settings = Card(30, 150, 780, 260, Theme.Accent);
        content.Controls.Add(settings);
        AddCardTitle(settings, "Store appearance & rotation", "Changes are accepted by the selected server and broadcast to online players.");

        shopTitle = LabelledText(settings, "STORE TITLE", 24, 82, 344);
        shopSubtitle = LabelledText(settings, "STORE SUBTITLE", 388, 82, 368);
        shopAccent = LabelledText(settings, "ACCENT COLOUR", 24, 146, 150);
        shopPanel = LabelledText(settings, "PANEL COLOUR", 190, 146, 150);

        Label styleLabel = LabelOf("STYLE", 8F, FontStyle.Bold, Theme.Muted);
        styleLabel.AutoSize = true;
        styleLabel.Location = new Point(356, 146);
        settings.Controls.Add(styleLabel);
        shopStyle = new ComboBox();
        shopStyle.Location = new Point(356, 166);
        shopStyle.Size = new Size(116, 28);
        shopStyle.DropDownStyle = ComboBoxStyle.DropDownList;
        shopStyle.Items.AddRange(new object[] { "valorant", "compact" });
        shopStyle.BackColor = Theme.Surface;
        shopStyle.ForeColor = Theme.Text;
        settings.Controls.Add(shopStyle);

        shopOfferCount = LabelledNumber(settings, "OFFERS", 488, 146, 1, 8);
        shopRotationHours = LabelledNumber(settings, "ROTATION HOURS", 582, 146, 1, 168);
        shopCasesEnabled = ConsoleCheckBox("Featured cases", 24, 216);
        shopOffersEnabled = ConsoleCheckBox("Personal offers", 184, 216);
        settings.Controls.Add(shopCasesEnabled);
        settings.Controls.Add(shopOffersEnabled);

        Panel catalog = Card(30, 430, 780, 410, Theme.Accent);
        content.Controls.Add(catalog);
        AddCardTitle(catalog, "Skin catalogue", "Set each eligible skin's price and whether it can enter a player's rotation.");
        shopSkins = ShopGrid();
        shopSkins.Location = new Point(24, 78);
        shopSkins.Size = new Size(732, 308);
        catalog.Controls.Add(shopSkins);

        if (shopDocument != null) FillShopFields();
        load.Click += delegate { LoadLiveShop(); };
        save.Click += delegate { SaveLiveShop(); };
    }

    private void LoadLiveShop()
    {
        GameServerProfile profile = RequireGameServer();
        if (profile == null) return;
        SetStatus("Loading live skin shop from " + profile.Name + "…", Theme.Accent);
        StartBusy("Loading live skin shop…", Theme.Accent);
        GameServerRuntime.SendAsync(profile, "GET", "/v1/shop", null, delegate(GameServerResult result)
        {
            if (IsDisposed) return;
            BeginInvoke((MethodInvoker)delegate
            {
                if (!result.Success)
                {
                    SetStatus("Skin shop could not be loaded.", Theme.Danger);
                    FinishBusy("Shop load failed", Theme.Danger);
                    GDialog.ShowMessage(this, "Shop load failed", result.Message, Theme.Danger);
                    return;
                }
                try
                {
                    JavaScriptSerializer serializer = JsonSerializer();
                    shopDocument = serializer.Deserialize<ShopDocument>(result.Body);
                    if (shopDocument == null) throw new InvalidOperationException("The server returned an empty shop document.");
                    ShowPage(PageKind.Shop, false);
                    SetStatus("Live skin shop loaded from " + profile.Name + ".", Theme.Success);
                    FinishBusy("Shop editor ready", Theme.Success);
                }
                catch (Exception exception)
                {
                    SetStatus("Shop response could not be read.", Theme.Danger);
                    FinishBusy("Shop load failed", Theme.Danger);
                    GDialog.ShowMessage(this, "Shop response error", exception.Message, Theme.Danger);
                }
            });
        });
    }

    private void SaveLiveShop()
    {
        GameServerProfile profile = RequireGameServer();
        if (profile == null || shopDocument == null)
        {
            if (profile != null) GDialog.ShowMessage(this, "Load the shop first", "Load the selected server's live shop before editing it.", Theme.Danger);
            return;
        }
        string error;
        if (!ApplyShopFields(out error))
        {
            GDialog.ShowMessage(this, "Check shop settings", error, Theme.Danger);
            return;
        }
        string json = JsonSerializer().Serialize(shopDocument);
        SetStatus("Publishing skin-shop changes to " + profile.Name + "…", Theme.Accent);
        StartBusy("Updating the live skin shop…", Theme.Accent);
        GameServerRuntime.SendAsync(profile, "PUT", "/v1/shop", json, delegate(GameServerResult result)
        {
            if (IsDisposed) return;
            BeginInvoke((MethodInvoker)delegate
            {
                if (!result.Success)
                {
                    SetStatus("Skin-shop update failed.", Theme.Danger);
                    FinishBusy("Shop update failed", Theme.Danger);
                    GDialog.ShowMessage(this, "Shop update failed", result.Message, Theme.Danger);
                    return;
                }
                shopDocument = JsonSerializer().Deserialize<ShopDocument>(result.Body);
                ShowPage(PageKind.Shop, false);
                SetStatus("Skin shop updated live on " + profile.Name + ".", Theme.Success);
                FinishBusy("Shop is live", Theme.Success);
            });
        });
    }

    private void RenderDeveloper()
    {
        AddPageHeader("Developer configuration", "Owner-only live configuration moved out of G'Express", Theme.Purple);
        GameServerProfile active = GameServerRuntime.ActiveProfile();

        GButton load = new GButton("Load live config", Theme.Purple, Color.FromArgb(181, 104, 255));
        load.Location = new Point(30, 94);
        load.Size = new Size(148, 38);
        content.Controls.Add(load);
        GButton publish = new GButton("Validate & publish", Theme.Surface, Theme.SurfaceHover);
        publish.Location = new Point(188, 94);
        publish.Size = new Size(164, 38);
        content.Controls.Add(publish);

        Label target = LabelOf(active == null ? "NO GAME SERVER SELECTED" : "TARGET  /  " + active.Name,
            8F, FontStyle.Bold, active == null ? Theme.Danger : Theme.Blue);
        target.AutoSize = true;
        target.Location = new Point(370, 106);
        content.Controls.Add(target);

        Panel editor = Card(30, 150, 780, 530, Theme.Purple);
        content.Controls.Add(editor);
        AddCardTitle(editor, "Complete G'Express configuration", "The server validates, migrates, persists, and broadcasts accepted values live.");
        developerJson = new RichTextBox();
        developerJson.Location = new Point(24, 80);
        developerJson.Size = new Size(732, 422);
        developerJson.BackColor = Color.FromArgb(10, 14, 20);
        developerJson.ForeColor = Theme.Text;
        developerJson.BorderStyle = BorderStyle.FixedSingle;
        developerJson.Font = new Font("Consolas", 9.5F, FontStyle.Regular, GraphicsUnit.Point);
        developerJson.AcceptsTab = true;
        developerJson.WordWrap = false;
        editor.Controls.Add(developerJson);

        load.Click += delegate { LoadDeveloperConfig(); };
        publish.Click += delegate { SaveDeveloperConfig(); };
    }

    private void LoadDeveloperConfig()
    {
        GameServerProfile profile = RequireGameServer();
        if (profile == null) return;
        SetStatus("Loading developer configuration from " + profile.Name + "…", Theme.Purple);
        StartBusy("Loading developer configuration…", Theme.Purple);
        GameServerRuntime.SendAsync(profile, "GET", "/v1/developer", null, delegate(GameServerResult result)
        {
            if (IsDisposed) return;
            BeginInvoke((MethodInvoker)delegate
            {
                if (!result.Success)
                {
                    SetStatus("Developer config could not be loaded.", Theme.Danger);
                    FinishBusy("Config load failed", Theme.Danger);
                    GDialog.ShowMessage(this, "Config load failed", result.Message, Theme.Danger);
                    return;
                }
                developerJson.Text = PrettyJson(result.Body);
                SetStatus("Developer configuration loaded from " + profile.Name + ".", Theme.Success);
                FinishBusy("Developer config ready", Theme.Success);
            });
        });
    }

    private void SaveDeveloperConfig()
    {
        GameServerProfile profile = RequireGameServer();
        if (profile == null) return;
        string json = developerJson == null ? "" : developerJson.Text.Trim();
        try
        {
            object parsed = JsonSerializer().DeserializeObject(json);
            if (!(parsed is Dictionary<string, object>)) throw new InvalidOperationException("The root value must be a JSON object.");
        }
        catch (Exception exception)
        {
            GDialog.ShowMessage(this, "Invalid JSON", exception.Message, Theme.Danger);
            return;
        }
        if (!GDialog.Confirm(this, "Publish developer config",
            "Apply this complete configuration to “" + profile.Name + "” and update connected players live?", Theme.Purple)) return;
        SetStatus("Publishing developer configuration to " + profile.Name + "…", Theme.Purple);
        StartBusy("Validating and publishing…", Theme.Purple);
        GameServerRuntime.SendAsync(profile, "PUT", "/v1/developer", json, delegate(GameServerResult result)
        {
            if (IsDisposed) return;
            BeginInvoke((MethodInvoker)delegate
            {
                if (!result.Success)
                {
                    SetStatus("Developer configuration was rejected.", Theme.Danger);
                    FinishBusy("Config update failed", Theme.Danger);
                    GDialog.ShowMessage(this, "Config update failed", result.Message, Theme.Danger);
                    return;
                }
                developerJson.Text = PrettyJson(result.Body);
                SetStatus("Developer configuration is live on " + profile.Name + ".", Theme.Success);
                FinishBusy("Configuration is live", Theme.Success);
            });
        });
    }

    private GameServerProfile RequireGameServer()
    {
        GameServerProfile profile = GameServerRuntime.ActiveProfile();
        if (profile != null) return profile;
        GDialog.ShowMessage(this, "Game server required", "Open Game servers, save a profile, and select it first.", Theme.Danger);
        return null;
    }

    private void FillShopFields()
    {
        if (shopDocument == null || shopTitle == null) return;
        shopTitle.Text = shopDocument.title ?? "";
        shopSubtitle.Text = shopDocument.subtitle ?? "";
        shopAccent.Text = shopDocument.accentColor ?? "#FFD166";
        shopPanel.Text = shopDocument.panelColor ?? "#272E36";
        shopStyle.SelectedItem = string.Equals(shopDocument.style, "compact", StringComparison.OrdinalIgnoreCase)
            ? "compact" : "valorant";
        shopOfferCount.Value = Math.Max(shopOfferCount.Minimum, Math.Min(shopOfferCount.Maximum, shopDocument.offerCount));
        shopRotationHours.Value = Math.Max(shopRotationHours.Minimum, Math.Min(shopRotationHours.Maximum, shopDocument.rotationHours));
        shopCasesEnabled.Checked = shopDocument.casesEnabled;
        shopOffersEnabled.Checked = shopDocument.dailyOffersEnabled;
        shopSkins.Rows.Clear();
        if (shopDocument.skins == null) return;
        for (int index = 0; index < shopDocument.skins.Length; index++)
        {
            ShopSkinDocument skin = shopDocument.skins[index];
            shopSkins.Rows.Add(skin.type, skin.skin, skin.price, skin.enabled);
        }
    }

    private bool ApplyShopFields(out string error)
    {
        error = null;
        if (shopTitle.Text.Trim().Length == 0 || shopSubtitle.Text.Trim().Length == 0)
        {
            error = "Enter a store title and subtitle.";
            return false;
        }
        if (!Regex.IsMatch(shopAccent.Text.Trim(), "^#[0-9a-fA-F]{6}$")
            || !Regex.IsMatch(shopPanel.Text.Trim(), "^#[0-9a-fA-F]{6}$"))
        {
            error = "Accent and panel colours must use six-digit hex values such as #FFD166.";
            return false;
        }
        List<ShopSkinDocument> skins = new List<ShopSkinDocument>();
        for (int index = 0; index < shopSkins.Rows.Count; index++)
        {
            DataGridViewRow row = shopSkins.Rows[index];
            if (row.IsNewRow) continue;
            int price;
            if (!int.TryParse(Convert.ToString(row.Cells[2].Value), out price) || price < 0 || price > 1000000)
            {
                error = "Every skin price must be a whole number from 0 to 1,000,000.";
                return false;
            }
            skins.Add(new ShopSkinDocument
            {
                type = Convert.ToString(row.Cells[0].Value),
                skin = Convert.ToString(row.Cells[1].Value),
                price = price,
                enabled = Convert.ToBoolean(row.Cells[3].Value ?? false)
            });
        }
        shopDocument.title = shopTitle.Text.Trim();
        shopDocument.subtitle = shopSubtitle.Text.Trim();
        shopDocument.accentColor = shopAccent.Text.Trim().ToUpperInvariant();
        shopDocument.panelColor = shopPanel.Text.Trim().ToUpperInvariant();
        shopDocument.style = Convert.ToString(shopStyle.SelectedItem) ?? "valorant";
        shopDocument.offerCount = Decimal.ToInt32(shopOfferCount.Value);
        shopDocument.rotationHours = Decimal.ToInt32(shopRotationHours.Value);
        shopDocument.casesEnabled = shopCasesEnabled.Checked;
        shopDocument.dailyOffersEnabled = shopOffersEnabled.Checked;
        shopDocument.skins = skins.ToArray();
        return true;
    }

    private static TextBox LabelledText(Panel parent, string label, int left, int top, int width)
    {
        Label heading = LabelOf(label, 8F, FontStyle.Bold, Theme.Muted);
        heading.AutoSize = true;
        heading.Location = new Point(left, top);
        parent.Controls.Add(heading);
        TextBox field = TextField(left, top + 20, width);
        parent.Controls.Add(field);
        return field;
    }

    private static NumericUpDown LabelledNumber(Panel parent, string label, int left, int top, int minimum, int maximum)
    {
        Label heading = LabelOf(label, 8F, FontStyle.Bold, Theme.Muted);
        heading.AutoSize = true;
        heading.Location = new Point(left, top);
        parent.Controls.Add(heading);
        NumericUpDown number = new NumericUpDown();
        number.Location = new Point(left, top + 20);
        number.Size = new Size(78, 28);
        number.Minimum = minimum;
        number.Maximum = maximum;
        number.BackColor = Theme.Surface;
        number.ForeColor = Theme.Text;
        number.BorderStyle = BorderStyle.FixedSingle;
        parent.Controls.Add(number);
        return number;
    }

    private static CheckBox ConsoleCheckBox(string text, int left, int top)
    {
        CheckBox check = new CheckBox();
        check.Text = text;
        check.AutoSize = true;
        check.Location = new Point(left, top);
        check.Font = Theme.Font(9F, FontStyle.Regular);
        check.ForeColor = Theme.Text;
        check.BackColor = Color.Transparent;
        return check;
    }

    private static DataGridView ShopGrid()
    {
        DataGridView grid = new DataGridView();
        grid.AllowUserToAddRows = false;
        grid.AllowUserToDeleteRows = false;
        grid.AllowUserToResizeRows = false;
        grid.RowHeadersVisible = false;
        grid.BackgroundColor = Color.FromArgb(10, 14, 20);
        grid.BorderStyle = BorderStyle.FixedSingle;
        grid.GridColor = Theme.Line;
        grid.EnableHeadersVisualStyles = false;
        grid.ColumnHeadersDefaultCellStyle.BackColor = Theme.Surface;
        grid.ColumnHeadersDefaultCellStyle.ForeColor = Theme.Text;
        grid.ColumnHeadersDefaultCellStyle.Font = Theme.Font(8.5F, FontStyle.Bold);
        grid.DefaultCellStyle.BackColor = Theme.Panel;
        grid.DefaultCellStyle.ForeColor = Theme.Text;
        grid.DefaultCellStyle.SelectionBackColor = Theme.SurfaceHover;
        grid.DefaultCellStyle.SelectionForeColor = Theme.Text;
        grid.DefaultCellStyle.Font = Theme.Font(9F, FontStyle.Regular);
        grid.Columns.Add(new DataGridViewTextBoxColumn { HeaderText = "TYPE", ReadOnly = true, Width = 110 });
        grid.Columns.Add(new DataGridViewTextBoxColumn { HeaderText = "SKIN", ReadOnly = true, AutoSizeMode = DataGridViewAutoSizeColumnMode.Fill });
        grid.Columns.Add(new DataGridViewTextBoxColumn { HeaderText = "PRICE", Width = 130 });
        grid.Columns.Add(new DataGridViewCheckBoxColumn { HeaderText = "IN ROTATION", Width = 130 });
        return grid;
    }

    private static JavaScriptSerializer JsonSerializer()
    {
        JavaScriptSerializer serializer = new JavaScriptSerializer();
        serializer.MaxJsonLength = 2 * 1024 * 1024;
        serializer.RecursionLimit = 128;
        return serializer;
    }

    private static string PrettyJson(string json)
    {
        object value = JsonSerializer().DeserializeObject(json);
        return JsonPrettyPrinter.Format(value, 0);
    }
}

internal sealed class ShopDocument
{
    public bool casesEnabled;
    public bool dailyOffersEnabled;
    public int offerCount;
    public int rotationHours;
    public string title;
    public string subtitle;
    public string style;
    public string accentColor;
    public string panelColor;
    public ShopSkinDocument[] skins;
}

internal sealed class ShopSkinDocument
{
    public string type;
    public string skin;
    public int price;
    public bool enabled;
}

internal sealed class GameServerProfile
{
    public string Name;
    public string Address;
    public string Token;
}

internal sealed class StoredGameServerProfile
{
    public string name;
    public string address;
    public string protectedToken;
}

internal sealed class GameServerResult
{
    public readonly int StatusCode;
    public readonly string Body;
    public readonly string Message;
    public bool Success { get { return StatusCode >= 200 && StatusCode < 300; } }

    public GameServerResult(int statusCode, string body, string message)
    {
        StatusCode = statusCode;
        Body = body ?? "";
        Message = string.IsNullOrWhiteSpace(message) ? "The game-server request failed." : message;
    }
}

internal static class GameServerRuntime
{
    private static readonly string ConfigDirectory = Path.Combine(
        Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData),
        "GNetworkAdminConsole");
    private static readonly string ProfilesFile = Path.Combine(ConfigDirectory, "game-servers.json");
    private static readonly string ActiveFile = Path.Combine(ConfigDirectory, "active-game-server.txt");
    private static readonly byte[] Entropy = Encoding.UTF8.GetBytes("GNetworkAdminConsole.GameServerProfiles.v1");
    private static readonly object Sync = new object();

    public static bool SelfTest()
    {
        try
        {
            GameServerProfile profile;
            string error;
            if (!TryCreate("Local", "http://127.0.0.1:8765", new string('x', 32), out profile, out error)) return false;
            if (!TryCreate("Remote", "203.0.113.10:30123", new string('x', 32), out profile, out error)) return false;
            if (profile.Address != "http://203.0.113.10:30123") return false;
            if (TryCreate("Bad", "file:///server", new string('x', 32), out profile, out error)) return false;
            if (!SecureProtocol.SelfTest()) return false;
            byte[] clear = Encoding.UTF8.GetBytes("owner-token-self-test");
            byte[] encrypted = ProtectedData.Protect(clear, Entropy, DataProtectionScope.CurrentUser);
            byte[] restored = ProtectedData.Unprotect(encrypted, Entropy, DataProtectionScope.CurrentUser);
            bool matches = Encoding.UTF8.GetString(restored) == "owner-token-self-test";
            Array.Clear(clear, 0, clear.Length);
            Array.Clear(restored, 0, restored.Length);
            return matches;
        }
        catch
        {
            return false;
        }
    }

    public static bool TryCreate(string name, string address, string token,
        out GameServerProfile profile, out string error)
    {
        profile = null;
        error = null;
        name = (name ?? "").Trim();
        address = (address ?? "").Trim().TrimEnd('/');
        token = (token ?? "").Trim();
        Uri uri;
        if (name.Length == 0 || name.Length > 64)
        {
            error = "Enter a profile name up to 64 characters.";
            return false;
        }
        if (address.IndexOf("://", StringComparison.Ordinal) < 0) address = "http://" + address;
        if (!Uri.TryCreate(address, UriKind.Absolute, out uri)
            || (uri.Scheme != Uri.UriSchemeHttp && uri.Scheme != Uri.UriSchemeHttps))
        {
            error = "Enter the server IP or hostname followed by its allocated port.";
            return false;
        }
        if (!string.IsNullOrEmpty(uri.UserInfo) || !string.IsNullOrEmpty(uri.Query)
            || !string.IsNullOrEmpty(uri.Fragment) || (uri.AbsolutePath != "/" && uri.AbsolutePath.Length != 0))
        {
            error = "Enter only the server address and allocated port, without a path, login, query, or fragment.";
            return false;
        }
        if (uri.Scheme == Uri.UriSchemeHttp && !IsLoopbackHost(uri.Host) && uri.IsDefaultPort)
        {
            error = "Include the allocated Wepwawet port after the server address.";
            return false;
        }
        if (token.Length < 24)
        {
            error = "Paste the owner token from the selected server's config/gexpress-console.json file.";
            return false;
        }
        profile = new GameServerProfile
        {
            Name = name,
            Address = uri.GetLeftPart(UriPartial.Authority).TrimEnd('/'),
            Token = token
        };
        return true;
    }

    private static bool IsLoopbackHost(string host)
    {
        if (string.Equals(host, "localhost", StringComparison.OrdinalIgnoreCase)) return true;
        IPAddress address;
        return IPAddress.TryParse(host, out address) && IPAddress.IsLoopback(address);
    }

    public static List<GameServerProfile> LoadProfiles()
    {
        lock (Sync)
        {
            List<GameServerProfile> profiles = new List<GameServerProfile>();
            try
            {
                if (!File.Exists(ProfilesFile)) return profiles;
                JavaScriptSerializer serializer = new JavaScriptSerializer();
                StoredGameServerProfile[] stored = serializer.Deserialize<StoredGameServerProfile[]>(
                    File.ReadAllText(ProfilesFile, Encoding.UTF8));
                if (stored == null) return profiles;
                for (int index = 0; index < stored.Length; index++)
                {
                    try
                    {
                        byte[] encrypted = Convert.FromBase64String(stored[index].protectedToken ?? "");
                        byte[] clear = ProtectedData.Unprotect(encrypted, Entropy, DataProtectionScope.CurrentUser);
                        profiles.Add(new GameServerProfile
                        {
                            Name = stored[index].name,
                            Address = stored[index].address,
                            Token = Encoding.UTF8.GetString(clear)
                        });
                        Array.Clear(clear, 0, clear.Length);
                    }
                    catch { }
                }
            }
            catch { }
            return profiles.OrderBy(profile => profile.Name, StringComparer.OrdinalIgnoreCase).ToList();
        }
    }

    public static GameServerProfile Find(string name)
    {
        if (string.IsNullOrWhiteSpace(name)) return null;
        return LoadProfiles().FirstOrDefault(profile => string.Equals(profile.Name, name, StringComparison.OrdinalIgnoreCase));
    }

    public static GameServerProfile ActiveProfile()
    {
        try
        {
            if (!File.Exists(ActiveFile)) return null;
            return Find(File.ReadAllText(ActiveFile, Encoding.UTF8).Trim());
        }
        catch { return null; }
    }

    public static void SetActive(string name)
    {
        Directory.CreateDirectory(ConfigDirectory);
        File.WriteAllText(ActiveFile, name ?? "", new UTF8Encoding(false));
    }

    public static void SaveProfile(GameServerProfile profile)
    {
        lock (Sync)
        {
            List<GameServerProfile> profiles = LoadProfiles();
            profiles.RemoveAll(existing => string.Equals(existing.Name, profile.Name, StringComparison.OrdinalIgnoreCase));
            profiles.Add(profile);
            WriteProfiles(profiles);
            SetActive(profile.Name);
        }
    }

    public static void DeleteProfile(string name)
    {
        lock (Sync)
        {
            List<GameServerProfile> profiles = LoadProfiles();
            profiles.RemoveAll(existing => string.Equals(existing.Name, name, StringComparison.OrdinalIgnoreCase));
            WriteProfiles(profiles);
            GameServerProfile active = ActiveProfile();
            if (active == null && File.Exists(ActiveFile)) File.Delete(ActiveFile);
        }
    }

    private static void WriteProfiles(List<GameServerProfile> profiles)
    {
        Directory.CreateDirectory(ConfigDirectory);
        List<StoredGameServerProfile> stored = new List<StoredGameServerProfile>();
        for (int index = 0; index < profiles.Count; index++)
        {
            byte[] clear = Encoding.UTF8.GetBytes(profiles[index].Token ?? "");
            byte[] encrypted = ProtectedData.Protect(clear, Entropy, DataProtectionScope.CurrentUser);
            Array.Clear(clear, 0, clear.Length);
            stored.Add(new StoredGameServerProfile
            {
                name = profiles[index].Name,
                address = profiles[index].Address,
                protectedToken = Convert.ToBase64String(encrypted)
            });
        }
        JavaScriptSerializer serializer = new JavaScriptSerializer();
        File.WriteAllText(ProfilesFile, serializer.Serialize(stored.ToArray()), new UTF8Encoding(false));
    }

    public static void SendAsync(GameServerProfile profile, string method, string path, string body,
        Action<GameServerResult> completion)
    {
        ThreadPool.QueueUserWorkItem(delegate
        {
            GameServerResult result;
            try { result = Send(profile, method, path, body); }
            catch (Exception exception) { result = new GameServerResult(0, "", exception.Message); }
            completion(result);
        });
    }

    private static GameServerResult Send(GameServerProfile profile, string method, string path, string body)
    {
        Uri address = new Uri(profile.Address);
        if (address.Scheme == Uri.UriSchemeHttp && !IsLoopbackHost(address.Host))
            return SendSecure(profile, method, path, body);
        return SendBearer(profile, method, path, body);
    }

    private static GameServerResult SendSecure(GameServerProfile profile, string method, string path, string body)
    {
        string requestNonce;
        string envelope = SecureProtocol.CreateRequest(profile.Token, method, path, body, out requestNonce);
        HttpWebRequest request = (HttpWebRequest)WebRequest.Create(profile.Address.TrimEnd('/') + "/v2/secure");
        request.Method = "POST";
        request.Accept = "application/vnd.gexpress.secure+json";
        request.ContentType = "application/vnd.gexpress.secure+json; charset=utf-8";
        request.Timeout = 10000;
        request.ReadWriteTimeout = 10000;
        request.KeepAlive = false;
        byte[] requestBytes = Encoding.UTF8.GetBytes(envelope);
        request.ContentLength = requestBytes.Length;
        using (Stream output = request.GetRequestStream()) output.Write(requestBytes, 0, requestBytes.Length);
        Array.Clear(requestBytes, 0, requestBytes.Length);
        try
        {
            using (HttpWebResponse response = (HttpWebResponse)request.GetResponse())
            using (StreamReader reader = new StreamReader(response.GetResponseStream(), Encoding.UTF8))
            {
                string responseBody = reader.ReadToEnd();
                SecureResponseDocument secure = SecureProtocol.OpenResponse(
                    profile.Token, requestNonce, responseBody);
                return new GameServerResult(secure.status, secure.body, secure.message);
            }
        }
        catch (WebException exception)
        {
            HttpWebResponse response = exception.Response as HttpWebResponse;
            if (response == null) return new GameServerResult(0, "", exception.Message);
            using (response)
            using (StreamReader reader = new StreamReader(response.GetResponseStream(), Encoding.UTF8))
            {
                string responseBody = reader.ReadToEnd();
                string message = ExtractError(responseBody, exception.Message);
                if ((int)response.StatusCode == 404)
                    message = "The server needs the updated G'Express mod with direct encrypted connections.";
                return new GameServerResult((int)response.StatusCode, responseBody, message);
            }
        }
        catch (CryptographicException)
        {
            return new GameServerResult(0, "",
                "The encrypted response could not be verified. Check the owner token and make sure both computers have the correct time.");
        }
    }

    private static GameServerResult SendBearer(GameServerProfile profile, string method, string path, string body)
    {
        HttpWebRequest request = (HttpWebRequest)WebRequest.Create(profile.Address.TrimEnd('/') + path);
        request.Method = method;
        request.Accept = "application/json";
        request.ContentType = "application/json; charset=utf-8";
        request.Headers[HttpRequestHeader.Authorization] = "Bearer " + profile.Token;
        request.Timeout = 10000;
        request.ReadWriteTimeout = 10000;
        request.KeepAlive = false;
        if (body != null)
        {
            byte[] bytes = Encoding.UTF8.GetBytes(body);
            request.ContentLength = bytes.Length;
            using (Stream output = request.GetRequestStream()) output.Write(bytes, 0, bytes.Length);
        }
        try
        {
            using (HttpWebResponse response = (HttpWebResponse)request.GetResponse())
            using (StreamReader reader = new StreamReader(response.GetResponseStream(), Encoding.UTF8))
            {
                string responseBody = reader.ReadToEnd();
                return new GameServerResult((int)response.StatusCode, responseBody, response.StatusDescription);
            }
        }
        catch (WebException exception)
        {
            HttpWebResponse response = exception.Response as HttpWebResponse;
            if (response == null) return new GameServerResult(0, "", exception.Message);
            using (response)
            using (StreamReader reader = new StreamReader(response.GetResponseStream(), Encoding.UTF8))
            {
                string responseBody = reader.ReadToEnd();
                return new GameServerResult((int)response.StatusCode, responseBody, ExtractError(responseBody, exception.Message));
            }
        }
    }

    private static string ExtractError(string body, string fallback)
    {
        try
        {
            JavaScriptSerializer serializer = new JavaScriptSerializer();
            Dictionary<string, object> parsed = serializer.Deserialize<Dictionary<string, object>>(body);
            object value;
            if (parsed != null && parsed.TryGetValue("error", out value)) return Convert.ToString(value);
        }
        catch { }
        return string.IsNullOrWhiteSpace(body) ? fallback : body;
    }
}

internal sealed class SecureEnvelopeDocument
{
    public int version;
    public long timestamp;
    public string nonce;
    public string replyTo;
    public string iv;
    public string ciphertext;
    public string mac;
}

internal sealed class SecureRequestDocument
{
    public string method;
    public string path;
    public string body;
}

internal sealed class SecureResponseDocument
{
    public int status = 0;
    public string body = "";
    public string message = "";
}

internal static class SecureProtocol
{
    private const int Version = 1;
    private const long MaximumClockSkewSeconds = 120L;
    private static readonly byte[] HkdfSalt = Encoding.UTF8.GetBytes("GExpress owner bridge direct v1");
    private const string EncryptionInfo = "gexpress-direct-encryption-v1";
    private const string AuthenticationInfo = "gexpress-direct-authentication-v1";
    private static readonly DateTime UnixEpoch = new DateTime(1970, 1, 1, 0, 0, 0, DateTimeKind.Utc);

    public static bool SelfTest()
    {
        try
        {
            string token = "0123456789abcdefghijklmnopqrstuvwxyzABCDEFG";
            string nonce;
            string envelope = CreateRequest(token, "PUT", "/v1/shop", "{\"enabled\":true}", out nonce);
            SecureRequestDocument opened = OpenRequestForTest(token, envelope);
            if (opened.method != "PUT" || opened.path != "/v1/shop" || opened.body != "{\"enabled\":true}") return false;
            byte[] tampered = Encoding.UTF8.GetBytes(envelope);
            if (tampered.Length == 0) return false;
            tampered[tampered.Length / 2] ^= 1;
            string changed = Encoding.UTF8.GetString(tampered);
            Array.Clear(tampered, 0, tampered.Length);
            try
            {
                OpenRequestForTest(token, changed);
                return false;
            }
            catch { }
            return true;
        }
        catch
        {
            return false;
        }
    }

    public static string CreateRequest(string token, string method, string path, string body,
        out string requestNonce)
    {
        SecureRequestDocument document = new SecureRequestDocument
        {
            method = method,
            path = path,
            body = body
        };
        byte[] nonce = RandomBytes(16);
        byte[] iv = RandomBytes(16);
        requestNonce = Convert.ToBase64String(nonce);
        return Seal(token, new JavaScriptSerializer().Serialize(document), "", NowSeconds(), nonce, iv);
    }

    public static SecureResponseDocument OpenResponse(string token, string requestNonce, string json)
    {
        SecureEnvelopeDocument envelope = ParseEnvelope(json);
        ValidateTimestamp(envelope.timestamp);
        if (!FixedTimeEquals(Encoding.UTF8.GetBytes(requestNonce ?? ""),
            Encoding.UTF8.GetBytes(envelope.replyTo ?? "")))
            throw new CryptographicException("Encrypted response does not match its request.");
        byte[] clear = Open(token, envelope, requestNonce);
        try
        {
            SecureResponseDocument response = new JavaScriptSerializer().Deserialize<SecureResponseDocument>(
                Encoding.UTF8.GetString(clear));
            if (response == null) throw new CryptographicException("Encrypted response is invalid.");
            return response;
        }
        finally
        {
            Array.Clear(clear, 0, clear.Length);
        }
    }

    private static SecureRequestDocument OpenRequestForTest(string token, string json)
    {
        SecureEnvelopeDocument envelope = ParseEnvelope(json);
        ValidateTimestamp(envelope.timestamp);
        byte[] clear = Open(token, envelope, "");
        try
        {
            SecureRequestDocument request = new JavaScriptSerializer().Deserialize<SecureRequestDocument>(
                Encoding.UTF8.GetString(clear));
            if (request == null) throw new CryptographicException("Encrypted request is invalid.");
            return request;
        }
        finally
        {
            Array.Clear(clear, 0, clear.Length);
        }
    }

    private static string Seal(string token, string plaintext, string replyTo, long timestamp,
        byte[] nonce, byte[] iv)
    {
        byte[][] keys = DeriveKeys(token);
        byte[] clear = Encoding.UTF8.GetBytes(plaintext ?? "");
        byte[] encrypted = null;
        try
        {
            using (Aes aes = Aes.Create())
            {
                aes.KeySize = 256;
                aes.Mode = CipherMode.CBC;
                aes.Padding = PaddingMode.PKCS7;
                aes.Key = keys[0];
                aes.IV = iv;
                using (ICryptoTransform encryptor = aes.CreateEncryptor())
                    encrypted = encryptor.TransformFinalBlock(clear, 0, clear.Length);
            }
            SecureEnvelopeDocument envelope = new SecureEnvelopeDocument
            {
                version = Version,
                timestamp = timestamp,
                nonce = Convert.ToBase64String(nonce),
                replyTo = replyTo ?? "",
                iv = Convert.ToBase64String(iv),
                ciphertext = Convert.ToBase64String(encrypted)
            };
            envelope.mac = Convert.ToBase64String(ComputeHmac(keys[1], Canonical(envelope)));
            return new JavaScriptSerializer().Serialize(envelope);
        }
        finally
        {
            Array.Clear(clear, 0, clear.Length);
            if (encrypted != null) Array.Clear(encrypted, 0, encrypted.Length);
            ClearKeys(keys);
        }
    }

    private static byte[] Open(string token, SecureEnvelopeDocument envelope, string expectedReplyTo)
    {
        if (envelope.version != Version) throw new CryptographicException("Unsupported encrypted protocol version.");
        if (!FixedTimeEquals(Encoding.UTF8.GetBytes(expectedReplyTo ?? ""),
            Encoding.UTF8.GetBytes(envelope.replyTo ?? "")))
            throw new CryptographicException("Encrypted reply binding is invalid.");
        byte[] nonce;
        byte[] iv;
        byte[] ciphertext;
        byte[] suppliedMac;
        try
        {
            nonce = Convert.FromBase64String(envelope.nonce ?? "");
            iv = Convert.FromBase64String(envelope.iv ?? "");
            ciphertext = Convert.FromBase64String(envelope.ciphertext ?? "");
            suppliedMac = Convert.FromBase64String(envelope.mac ?? "");
        }
        catch (FormatException exception)
        {
            throw new CryptographicException("Encrypted envelope encoding is invalid.", exception);
        }
        if (nonce.Length != 16 || iv.Length != 16 || suppliedMac.Length != 32
            || ciphertext.Length == 0 || ciphertext.Length % 16 != 0)
            throw new CryptographicException("Encrypted envelope fields are invalid.");
        byte[][] keys = DeriveKeys(token);
        try
        {
            byte[] expectedMac = ComputeHmac(keys[1], Canonical(envelope));
            bool valid = FixedTimeEquals(expectedMac, suppliedMac);
            Array.Clear(expectedMac, 0, expectedMac.Length);
            if (!valid) throw new CryptographicException("Encrypted envelope authentication failed.");
            using (Aes aes = Aes.Create())
            {
                aes.KeySize = 256;
                aes.Mode = CipherMode.CBC;
                aes.Padding = PaddingMode.PKCS7;
                aes.Key = keys[0];
                aes.IV = iv;
                using (ICryptoTransform decryptor = aes.CreateDecryptor())
                    return decryptor.TransformFinalBlock(ciphertext, 0, ciphertext.Length);
            }
        }
        finally
        {
            Array.Clear(nonce, 0, nonce.Length);
            Array.Clear(iv, 0, iv.Length);
            Array.Clear(ciphertext, 0, ciphertext.Length);
            Array.Clear(suppliedMac, 0, suppliedMac.Length);
            ClearKeys(keys);
        }
    }

    private static SecureEnvelopeDocument ParseEnvelope(string json)
    {
        SecureEnvelopeDocument envelope;
        try { envelope = new JavaScriptSerializer().Deserialize<SecureEnvelopeDocument>(json); }
        catch (Exception exception) { throw new CryptographicException("Encrypted envelope is invalid.", exception); }
        if (envelope == null || envelope.nonce == null || envelope.iv == null
            || envelope.ciphertext == null || envelope.mac == null)
            throw new CryptographicException("Encrypted envelope is incomplete.");
        return envelope;
    }

    private static void ValidateTimestamp(long timestamp)
    {
        long now = NowSeconds();
        if (timestamp < now - MaximumClockSkewSeconds || timestamp > now + MaximumClockSkewSeconds)
            throw new CryptographicException("Encrypted response timestamp is outside the allowed window.");
    }

    private static byte[][] DeriveKeys(string token)
    {
        byte[] tokenBytes = Encoding.UTF8.GetBytes(token ?? "");
        byte[] prk = ComputeHmac(HkdfSalt, tokenBytes);
        try
        {
            return new byte[][] { Expand(prk, EncryptionInfo), Expand(prk, AuthenticationInfo) };
        }
        finally
        {
            Array.Clear(tokenBytes, 0, tokenBytes.Length);
            Array.Clear(prk, 0, prk.Length);
        }
    }

    private static byte[] Expand(byte[] prk, string information)
    {
        byte[] text = Encoding.UTF8.GetBytes(information);
        byte[] input = new byte[text.Length + 1];
        Buffer.BlockCopy(text, 0, input, 0, text.Length);
        input[input.Length - 1] = 1;
        try { return ComputeHmac(prk, input); }
        finally { Array.Clear(input, 0, input.Length); }
    }

    private static byte[] ComputeHmac(byte[] key, byte[] input)
    {
        using (HMACSHA256 hmac = new HMACSHA256(key)) return hmac.ComputeHash(input);
    }

    private static byte[] Canonical(SecureEnvelopeDocument envelope)
    {
        string text = envelope.version.ToString(System.Globalization.CultureInfo.InvariantCulture) + "\n"
            + envelope.timestamp.ToString(System.Globalization.CultureInfo.InvariantCulture) + "\n"
            + (envelope.nonce ?? "") + "\n" + (envelope.replyTo ?? "") + "\n"
            + (envelope.iv ?? "") + "\n" + (envelope.ciphertext ?? "");
        return Encoding.UTF8.GetBytes(text);
    }

    private static bool FixedTimeEquals(byte[] left, byte[] right)
    {
        int difference = left.Length ^ right.Length;
        int length = Math.Max(left.Length, right.Length);
        for (int index = 0; index < length; index++)
        {
            byte a = index < left.Length ? left[index] : (byte)0;
            byte b = index < right.Length ? right[index] : (byte)0;
            difference |= a ^ b;
        }
        return difference == 0;
    }

    private static byte[] RandomBytes(int length)
    {
        byte[] bytes = new byte[length];
        using (RandomNumberGenerator random = RandomNumberGenerator.Create()) random.GetBytes(bytes);
        return bytes;
    }

    private static long NowSeconds()
    {
        return (long)(DateTime.UtcNow - UnixEpoch).TotalSeconds;
    }

    private static void ClearKeys(byte[][] keys)
    {
        if (keys == null) return;
        for (int index = 0; index < keys.Length; index++)
            if (keys[index] != null) Array.Clear(keys[index], 0, keys[index].Length);
    }
}

internal static class JsonPrettyPrinter
{
    public static string Format(object value, int depth)
    {
        if (value == null) return "null";
        Dictionary<string, object> map = value as Dictionary<string, object>;
        if (map != null)
        {
            if (map.Count == 0) return "{}";
            List<string> rows = new List<string>();
            foreach (KeyValuePair<string, object> item in map)
            {
                rows.Add(Indent(depth + 1) + Quote(item.Key) + ": " + Format(item.Value, depth + 1));
            }
            return "{\r\n" + string.Join(",\r\n", rows.ToArray()) + "\r\n" + Indent(depth) + "}";
        }
        object[] array = value as object[];
        if (array != null)
        {
            if (array.Length == 0) return "[]";
            List<string> rows = new List<string>();
            for (int index = 0; index < array.Length; index++)
                rows.Add(Indent(depth + 1) + Format(array[index], depth + 1));
            return "[\r\n" + string.Join(",\r\n", rows.ToArray()) + "\r\n" + Indent(depth) + "]";
        }
        string text = value as string;
        if (text != null) return Quote(text);
        if (value is bool) return (bool)value ? "true" : "false";
        return Convert.ToString(value, System.Globalization.CultureInfo.InvariantCulture);
    }

    private static string Quote(string value)
    {
        return new JavaScriptSerializer().Serialize(value ?? "");
    }

    private static string Indent(int depth)
    {
        return new string(' ', Math.Max(0, depth) * 2);
    }
}
