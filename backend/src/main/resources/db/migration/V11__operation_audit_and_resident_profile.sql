create table operation_audit_log (
  id bigint primary key auto_increment,
  operator_user_id bigint,
  module varchar(40) not null,
  action varchar(50) not null,
  request_method varchar(10) not null,
  object_path varchar(500) not null,
  result varchar(20) not null,
  status_code int not null,
  created_at timestamp(3) not null default current_timestamp(3),
  key idx_operation_audit_time (created_at, id),
  key idx_operation_audit_module_result (module, result, created_at),
  key idx_operation_audit_operator (operator_user_id, created_at),
  constraint fk_operation_audit_operator foreign key (operator_user_id) references sys_user (id) on delete set null,
  constraint ck_operation_audit_module check (module in ('ATTACHMENT', 'SYSTEM_MANAGEMENT')),
  constraint ck_operation_audit_result check (result in ('SUCCESS', 'FAILURE')),
  constraint ck_operation_audit_status check (status_code between 100 and 599)
);
