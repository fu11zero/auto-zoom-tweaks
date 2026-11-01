-- Options > UI > Camera: the vanilla "Auto zoom" row (one tickbox per player slot,
-- MainOptions:addUIPanel) is replaced by a table: per player On + a zoom level indoors,
-- in melee, outdoors and in a vehicle.
--
-- On/off stays the vanilla flag (Core.setAutoZoom, options.ini). The levels live in
-- ~/Zomboid/AutoZoomTweaks.properties and are used by the Java side (AutoZoom.java), which
-- also exports the AutoZoomTweaks_getLevel/setLevel globals. Without them (jar not approved
-- in ZombieBuddy) or without the vanilla row (game changed) the vanilla row stays.

require "OptionScreens/MainOptions"

AutoZoomTweaks = AutoZoomTweaks or {}

local SITUATIONS = { "indoors", "melee", "outdoors", "vehicle" }
-- MultiTextureFBO2.zoomLevelsDefault, independent of the "Zoom levels" tickboxes (manual zoom).
local LEVELS = { 25, 50, 75, 100, 125, 150, 175, 200, 225, 250 }

local function log(msg)
    print("[AutoZoomTweaks] " .. msg)
end

local function findOption(self, name)
    for _, option in ipairs(self.gameOptions.options) do
        if option.name == name then
            return option
        end
    end
    return nil
end

-- The ISLabel vanilla puts left of a tickbox (right-aligned at splitpoint, same y).
local function findLabel(panel, box, key)
    local text = getText(key)
    for _, child in pairs(panel:getChildren()) do
        if child.Type == "ISLabel" and child.name == text and math.abs(child:getY() - box:getY()) < 1 then
            return child
        end
    end
    return nil
end

local function findJoypadRow(panel, box)
    for i, row in ipairs(panel.joypadButtonsY) do
        for _, element in ipairs(row) do
            if element == box then
                return i
            end
        end
    end
    return nil
end

local function updateEnabled(rows)
    for _, row in ipairs(rows) do
        local on = row.tick:isSelected(1)
        for _, combo in ipairs(row.combos) do
            combo:setEnabled(on)
        end
    end
end

local function replaceRow(self, option)
    local box = option.control
    local panel = box.parent
    if not panel then
        log("WARN: auto zoom tickbox has no parent, vanilla row kept")
        return
    end
    local label = findLabel(panel, box, "UI_optionscreen_autozoom")
    local joypadIndex = findJoypadRow(panel, box)
    if not label or not joypadIndex then
        log("WARN: vanilla auto zoom row not recognised, kept as is")
        return
    end

    local style = MainOptions.style
    local spacing = style.borderSpacing
    local rowHgt = style.buttonHeight
    local font = UIFont.Small
    local tm = getTextManager()
    local x0 = box:getX()
    local y0 = box:getY()
    local oldBottom = y0 + box:getHeight() + spacing

    -- Everything below the vanilla row moves down by the height difference; collect it first.
    local below = {}
    for _, child in pairs(panel:getChildren()) do
        if child ~= panel.vscroll and child ~= box and child:getY() > y0 + 1 then
            table.insert(below, child)
        end
    end

    box:setVisible(false)
    label:setHeight(rowHgt)

    local headers = {}
    local comboW = tm:MeasureStringX(font, "250%") + rowHgt + spacing * 4
    for i, id in ipairs(SITUATIONS) do
        headers[i] = getText("UI_AutoZoomTweaks_" .. id)
        comboW = math.max(comboW, tm:MeasureStringX(font, headers[i]) + spacing * 2)
    end

    local rows = {}
    local onTick = function()
        updateEnabled(rows)
        option:invokeOnChangeEvent()
    end
    local onCombo = function()
        option:invokeOnChangeEvent()
    end

    -- Player names first: their width sets where the combos start.
    local y = y0 + rowHgt + spacing
    local tickW = 0
    for i = 1, 4 do
        local tick = ISTickBox:new(x0, y, rowHgt, rowHgt, "", nil, onTick)
        tick.choicesColor = { r = 1, g = 1, b = 1, a = 1 }
        tick:initialise()
        panel:addChild(tick)
        tick:addOption(getText("UI_optionscreen_player" .. i), nil)
        -- No autoWidth: the tickbox stays as wide as the box and the combos cover the name.
        tick:setWidthToFit()
        tickW = math.max(tickW, tick:getWidth())
        rows[i] = { tick = tick, combos = {} }
        y = y + rowHgt + spacing
    end
    local comboX0 = x0 + tickW + spacing * 4
    local step = comboW + spacing * 2

    local onLabel = ISLabel:new(x0, y0, rowHgt, getText("UI_AutoZoomTweaks_on"), 0.7, 0.7, 0.7, 1, font, true)
    onLabel:initialise()
    panel:addChild(onLabel)
    for i = 1, #SITUATIONS do
        local header = ISLabel:new(comboX0 + (i - 1) * step, y0, rowHgt, headers[i], 0.7, 0.7, 0.7, 1, font, true)
        header:initialise()
        panel:addChild(header)
    end

    local joypadRows = {}
    for p = 1, 4 do
        local row = rows[p]
        local line = { row.tick }
        for i = 1, #SITUATIONS do
            local combo = ISComboBox:new(comboX0 + (i - 1) * step, row.tick:getY(), comboW, rowHgt, nil, onCombo)
            combo:initialise()
            for _, percent in ipairs(LEVELS) do
                combo:addOptionWithData(percent .. "%", percent)
            end
            panel:addChild(combo)
            row.combos[i] = combo
            table.insert(line, combo)
        end
        joypadRows[p] = line
    end

    -- As wide as the table: MainOptions:centerChildrenX centres the page by its widest child.
    local desc = ISRichTextPanel:new(x0, y, comboX0 + #SITUATIONS * step - spacing * 2 - x0, 0)
    desc.background = false
    desc.autosetheight = true
    desc:initialise()
    panel:addChild(desc)
    desc:setText(getText("UI_AutoZoomTweaks_desc"))
    desc:paginate()
    y = y + desc:getHeight() + spacing

    local dy = y - oldBottom
    for _, child in ipairs(below) do
        child:setY(child:getY() + dy)
    end
    panel:setScrollHeight(panel:getScrollHeight() + dy)

    -- Gamepad navigation: one line per player in place of the vanilla tickbox line.
    table.remove(panel.joypadButtonsY, joypadIndex)
    for p = #joypadRows, 1, -1 do
        table.insert(panel.joypadButtonsY, joypadIndex, joypadRows[p])
        for _, element in ipairs(joypadRows[p]) do
            table.insert(panel.allJoypadButtons, element)
        end
    end

    function option.toUI(opt)
        for p, row in ipairs(rows) do
            row.tick:setSelected(1, getCore():getAutoZoom(p - 1))
            for i, id in ipairs(SITUATIONS) do
                row.combos[i]:selectData(AutoZoomTweaks_getLevel(p - 1, id))
            end
        end
        updateEnabled(rows)
    end

    function option.apply(opt)
        for p, row in ipairs(rows) do
            getCore():setAutoZoom(p - 1, row.tick:isSelected(1))
            for i, id in ipairs(SITUATIONS) do
                local combo = row.combos[i]
                local percent = combo:getOptionData(combo.selected)
                if percent then
                    AutoZoomTweaks_setLevel(p - 1, id, percent)
                end
            end
        end
    end

    log("auto zoom row replaced")
    return true
end

-- "Zoom levels" (MainOptions:addUIPanel, GameOption zoomLevels): with the table its only use
-- would be the steps of manual zoom, which players can't tell from the auto zoom levels. The
-- row is removed: hidden, everything below moved up, its gamepad line and GameOption dropped,
-- so options.ini keeps the value it had (manual zoom steps; the defaults of the table were
-- taken from it, Settings.java).
local function removeLevelsRow(self)
    local option = findOption(self, "zoomLevels")
    local box = option and option.control
    local panel = box and box.parent
    local label = panel and findLabel(panel, box, "UI_optionscreen_zoomlevels")
    local joypadIndex = panel and findJoypadRow(panel, box)
    if not label or not joypadIndex then
        log("WARN: vanilla zoom levels row not recognised, kept as is")
        return
    end
    local top = box:getY()
    local dy = box:getHeight() + MainOptions.style.borderSpacing
    for _, child in pairs(panel:getChildren()) do
        if child ~= panel.vscroll and child ~= box and child ~= label and child:getY() > top + 1 then
            child:setY(child:getY() - dy)
        end
    end
    box:setVisible(false)
    label:setVisible(false)
    panel:setScrollHeight(panel:getScrollHeight() - dy)
    table.remove(panel.joypadButtonsY, joypadIndex)
    self.gameOptions:remove(option)
end

local addUIPanel = MainOptions.addUIPanel
function MainOptions:addUIPanel()
    addUIPanel(self)
    if not AutoZoomTweaks_getLevel or not AutoZoomTweaks_setLevel then
        log("WARN: Java part not loaded (ZombieBuddy jar not approved?), vanilla auto zoom row kept")
        return
    end
    local option = findOption(self, "autoZoom")
    if not option then
        log("WARN: vanilla auto zoom option not found, vanilla row kept")
        return
    end
    local ok, result = pcall(replaceRow, self, option)
    if not ok then
        log("ERROR: can't replace the auto zoom row: " .. tostring(result))
    elseif result then
        ok, result = pcall(removeLevelsRow, self)
        if not ok then
            log("ERROR: can't remove the zoom levels row: " .. tostring(result))
        end
    end
end
