-- Fluent Bit Lua validation script
-- Validates required fields and marks invalid logs for DLQ

function validate_log(tag, timestamp, record)
    local required_fields = {
        "@timestamp",
        "log.level", 
        "service.name",
        "event_type"
    }
    
    local is_valid = true
    local missing_fields = {}
    
    -- Check for required fields
    for _, field in ipairs(required_fields) do
        local value = get_nested_field(record, field)
        if value == nil or value == "" then
            is_valid = false
            table.insert(missing_fields, field)
        end
    end
    
    -- Validate event_type is known
    if record["event_type"] then
        local valid_prefixes = {
            "APP_", "HTTP_", "WS_", "DB_", "KAFKA_",
            "CHAOS_", "POLICY_", "STATE_", "CONNECTOR_",
            "SECURITY_", "AUTH_", "RECOVERY_", "SCHEDULER_",
            "METRIC_", "ERROR_"
        }
        
        local event_type = record["event_type"]
        local prefix_valid = false
        
        for _, prefix in ipairs(valid_prefixes) do
            if string.sub(event_type, 1, #prefix) == prefix then
                prefix_valid = true
                break
            end
        end
        
        if not prefix_valid then
            is_valid = false
            table.insert(missing_fields, "event_type (invalid prefix)")
        end
    end
    
    -- Mark record with validation result
    record["_valid"] = tostring(is_valid)
    
    if not is_valid then
        record["_validation_error"] = table.concat(missing_fields, ", ")
    end
    
    return 1, timestamp, record
end

-- Helper to get nested field like "log.level"
function get_nested_field(record, field)
    local parts = {}
    for part in string.gmatch(field, "[^.]+") do
        table.insert(parts, part)
    end
    
    local value = record
    for _, part in ipairs(parts) do
        if type(value) ~= "table" then
            return nil
        end
        value = value[part]
    end
    
    return value
end
