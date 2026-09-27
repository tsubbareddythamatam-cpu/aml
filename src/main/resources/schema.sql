CREATE DATABASE IF NOT EXISTS aml
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_unicode_ci;
USE aml;

CREATE TABLE IF NOT EXISTS users (
    id BIGINT NOT NULL AUTO_INCREMENT,
    first_name VARCHAR(255),
    last_name VARCHAR(255),
    email VARCHAR(255) NOT NULL,
    company_name VARCHAR(255),
    phone_number VARCHAR(255),
    country VARCHAR(255),
    password VARCHAR(255),
    role VARCHAR(255),
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    reset_token VARCHAR(255),
    reset_token_expiry DATETIME(6),
    PRIMARY KEY (id),
    UNIQUE KEY uq_users_email (email)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS customer_information (
    id BIGINT NOT NULL AUTO_INCREMENT,
    aml_reference_id VARCHAR(255),
    aml_date_time VARCHAR(255),
    aml_status VARCHAR(255),
    aml_type VARCHAR(255),
    full_name VARCHAR(255),
    individual_type VARCHAR(255),
    father_name VARCHAR(255),
    gender VARCHAR(255),
    date_of_birth VARCHAR(255),
    nationality VARCHAR(255),
    country_of_residence VARCHAR(255),
    resident_status VARCHAR(255),
    national_id_number VARCHAR(255),
    national_id_expiry VARCHAR(255),
    passport_number VARCHAR(255),
    passport_expiry VARCHAR(255),
    other_nationalities VARCHAR(255),
    is_crs VARCHAR(255),
    onboarded_by VARCHAR(255),
    onboarding_date VARCHAR(255),
    external_reference VARCHAR(255),
    record_last_updated VARCHAR(255),
    footer_text TEXT,
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS profile_information (
    id BIGINT NOT NULL AUTO_INCREMENT,
    work_type VARCHAR(255),
    industry VARCHAR(255),
    delivery_channel VARCHAR(255),
    relationship_start_date VARCHAR(255),
    products VARCHAR(255),
    product_offered VARCHAR(255),
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS contact_information (
    id BIGINT NOT NULL AUTO_INCREMENT,
    address TEXT,
    town_city VARCHAR(255),
    county_state VARCHAR(255),
    postal_code VARCHAR(255),
    contact_number VARCHAR(255),
    email_address VARCHAR(255),
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS pep_declaration (
    id BIGINT NOT NULL AUTO_INCREMENT,
    currently_hold_public_position VARCHAR(255),
    held_public_position_last_12_months VARCHAR(255),
    ever_held_public_position VARCHAR(255),
    diplomatic_immunity VARCHAR(255),
    relative_held_public_position_last_12_months VARCHAR(255),
    close_associate_held_public_position_last_12_months VARCHAR(255),
    court_conviction VARCHAR(255),
    details TEXT,
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS source_of_wealth_funds (
    id BIGINT NOT NULL AUTO_INCREMENT,
    source_of_wealth TEXT,
    source_of_wealth_other TEXT,
    source_of_funds TEXT,
    source_of_funds_other TEXT,
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS investment_range (
    id BIGINT NOT NULL AUTO_INCREMENT,
    investment_range VARCHAR(255),
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS ongoing_due_diligence (
    id BIGINT NOT NULL AUTO_INCREMENT,
    last_review VARCHAR(255),
    next_review VARCHAR(255),
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS signature_panel (
    id BIGINT NOT NULL AUTO_INCREMENT,
    signature TEXT,
    name VARCHAR(255),
    position VARCHAR(255),
    date VARCHAR(255),
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS customers (
    aml_id VARCHAR(50) NOT NULL,
    user_id BIGINT,
    external_ref_number VARCHAR(255),
    full_name VARCHAR(255),
    other_nationalities VARCHAR(255),
    delivery_channel VARCHAR(255),
    transaction_type VARCHAR(255),
    name_screening_hit BIT(1),
    documents_verification_hit BIT(1),
    risk_rating_hit BIT(1),
    status VARCHAR(255),
    onboarded_by_user VARCHAR(255),
    onboarded_by_company VARCHAR(255),
    comment TEXT,
    registered_at DATETIME(6),
    last_review_date DATE,
    report_generated_on DATETIME(6),
    company_name VARCHAR(255),
    position_in_company VARCHAR(255),
    customer_information_id BIGINT,
    profile_information_id BIGINT,
    contact_information_id BIGINT,
    pep_declaration_id BIGINT,
    source_of_wealth_funds_id BIGINT,
    investment_range_id BIGINT,
    ongoing_due_diligence_id BIGINT,
    signature_panel_id BIGINT,
    PRIMARY KEY (aml_id),
    UNIQUE KEY uq_customers_customer_information (customer_information_id),
    UNIQUE KEY uq_customers_profile_information (profile_information_id),
    UNIQUE KEY uq_customers_contact_information (contact_information_id),
    UNIQUE KEY uq_customers_pep_declaration (pep_declaration_id),
    UNIQUE KEY uq_customers_source_of_wealth_funds (source_of_wealth_funds_id),
    UNIQUE KEY uq_customers_investment_range (investment_range_id),
    UNIQUE KEY uq_customers_ongoing_due_diligence (ongoing_due_diligence_id),
    UNIQUE KEY uq_customers_signature_panel (signature_panel_id),
    KEY idx_customers_user_id (user_id),
    CONSTRAINT fk_customers_user
        FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE SET NULL,
    CONSTRAINT fk_customers_customer_information
        FOREIGN KEY (customer_information_id) REFERENCES customer_information (id),
    CONSTRAINT fk_customers_profile_information
        FOREIGN KEY (profile_information_id) REFERENCES profile_information (id),
    CONSTRAINT fk_customers_contact_information
        FOREIGN KEY (contact_information_id) REFERENCES contact_information (id),
    CONSTRAINT fk_customers_pep_declaration
        FOREIGN KEY (pep_declaration_id) REFERENCES pep_declaration (id),
    CONSTRAINT fk_customers_source_of_wealth_funds
        FOREIGN KEY (source_of_wealth_funds_id) REFERENCES source_of_wealth_funds (id),
    CONSTRAINT fk_customers_investment_range
        FOREIGN KEY (investment_range_id) REFERENCES investment_range (id),
    CONSTRAINT fk_customers_ongoing_due_diligence
        FOREIGN KEY (ongoing_due_diligence_id) REFERENCES ongoing_due_diligence (id),
    CONSTRAINT fk_customers_signature_panel
        FOREIGN KEY (signature_panel_id) REFERENCES signature_panel (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS risk_ratings_and_overrides (
    aml_id VARCHAR(50) NOT NULL,
    total_matches INT,
    resolved_matches INT,
    genuine_matches INT,
    not_genuine_matches INT,
    unresolved_matches INT,
    country_residence_score INT,
    country_residence_level VARCHAR(255),
    delivery_channel_score INT,
    delivery_channel_level VARCHAR(255),
    industry_score INT,
    industry_level VARCHAR(255),
    nationality_score INT,
    nationality_level VARCHAR(255),
    product_score INT,
    product_level VARCHAR(255),
    anti_spoofing_score INT,
    anti_spoofing_level VARCHAR(255),
    base_rating_score INT,
    base_rating_level VARCHAR(255),
    str_filed_override VARCHAR(255),
    sanction_hit_override VARCHAR(255),
    non_resident_override VARCHAR(255),
    sanctioned_residence_override VARCHAR(255),
    sanctioned_nationality_override VARCHAR(255),
    sanctioned_phone_code_override VARCHAR(255),
    pep_override VARCHAR(255),
    special_interest_override VARCHAR(255),
    doc_verification_override VARCHAR(255),
    adverse_media_override VARCHAR(255),
    transaction_override VARCHAR(255),
    overall_rating_calculated VARCHAR(255),
    overall_rating_final VARCHAR(255),
    PRIMARY KEY (aml_id),
    CONSTRAINT fk_risk_customer
        FOREIGN KEY (aml_id) REFERENCES customers (aml_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS screening_hit_details (
    id BIGINT NOT NULL AUTO_INCREMENT,
    aml_id VARCHAR(50) NOT NULL,
    hit_name VARCHAR(255),
    category VARCHAR(255),
    source VARCHAR(255),
    score INT,
    hit_determination VARCHAR(255),
    comments TEXT,
    PRIMARY KEY (id),
    KEY idx_screening_aml_id (aml_id),
    CONSTRAINT fk_hits_customer
        FOREIGN KEY (aml_id) REFERENCES customers (aml_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS audit_logs (
    id BIGINT NOT NULL AUTO_INCREMENT,
    aml_id VARCHAR(50) NOT NULL,
    action_date DATE,
    actioned_by VARCHAR(255),
    action_taken TEXT,
    PRIMARY KEY (id),
    KEY idx_audit_aml_id (aml_id),
    CONSTRAINT fk_audits_customer
        FOREIGN KEY (aml_id) REFERENCES customers (aml_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
