create table resident_sensitive_access_log (
  id bigint primary key auto_increment,
  operator_user_id bigint not null,
  resident_id bigint,
  action varchar(20) not null,
  field_type varchar(20) not null,
  purpose varchar(200),
  result_count int not null default 0,
  created_at datetime(3) not null default current_timestamp(3),
  key idx_sensitive_access_operator_time (operator_user_id, created_at),
  key idx_sensitive_access_resident_time (resident_id, created_at),
  constraint fk_sensitive_access_operator
    foreign key (operator_user_id) references sys_user (id) on delete restrict,
  constraint fk_sensitive_access_resident
    foreign key (resident_id) references resident (id) on delete set null,
  constraint ck_sensitive_access_action
    check (action in ('SEARCH', 'VIEW')),
  constraint ck_sensitive_access_field_type
    check (field_type in ('ID_CARD', 'PHONE', 'BOTH')),
  constraint ck_sensitive_access_result_count
    check (result_count >= 0),
  constraint ck_sensitive_access_purpose check (
    (action = 'SEARCH' and purpose is null)
    or (action = 'VIEW' and char_length(trim(purpose)) between 5 and 200)
  )
) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_0900_ai_ci;
