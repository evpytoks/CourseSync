using System;
using Microsoft.EntityFrameworkCore.Migrations;

#nullable disable

namespace CourseSync.Api.Migrations
{
    /// <inheritdoc />
    public partial class CalendarEventCrossGroupAndUserPreferences : Migration
    {
        /// <inheritdoc />
        protected override void Up(MigrationBuilder migrationBuilder)
        {
            migrationBuilder.AddColumn<string>(
                name: "calendar_event_type_colors",
                table: "users",
                type: "character varying(4000)",
                maxLength: 4000,
                nullable: false,
                defaultValue: "");

            migrationBuilder.AddColumn<Guid>(
                name: "course_id",
                table: "calendar_events",
                type: "uuid",
                nullable: true);

            migrationBuilder.AddColumn<string>(
                name: "event_type",
                table: "calendar_events",
                type: "character varying(30)",
                maxLength: 30,
                nullable: false,
                defaultValue: "Другое");

            migrationBuilder.Sql("UPDATE calendar_events SET event_type = 'Другое' WHERE event_type = '' OR event_type IS NULL;");

            migrationBuilder.CreateTable(
                name: "calendar_event_user_states",
                columns: table => new
                {
                    event_id = table.Column<Guid>(type: "uuid", nullable: false),
                    user_id = table.Column<Guid>(type: "uuid", nullable: false),
                    is_done = table.Column<bool>(type: "boolean", nullable: false, defaultValue: false)
                },
                constraints: table =>
                {
                    table.PrimaryKey("PK_calendar_event_user_states", x => new { x.event_id, x.user_id });
                    table.ForeignKey(
                        name: "FK_calendar_event_user_states_calendar_events_event_id",
                        column: x => x.event_id,
                        principalTable: "calendar_events",
                        principalColumn: "Id",
                        onDelete: ReferentialAction.Cascade);
                    table.ForeignKey(
                        name: "FK_calendar_event_user_states_users_user_id",
                        column: x => x.user_id,
                        principalTable: "users",
                        principalColumn: "Id",
                        onDelete: ReferentialAction.Cascade);
                });

            migrationBuilder.CreateIndex(
                name: "IX_calendar_events_course_id",
                table: "calendar_events",
                column: "course_id");

            migrationBuilder.CreateIndex(
                name: "IX_calendar_event_user_states_user_id",
                table: "calendar_event_user_states",
                column: "user_id");

            migrationBuilder.AddForeignKey(
                name: "FK_calendar_events_courses_course_id",
                table: "calendar_events",
                column: "course_id",
                principalTable: "courses",
                principalColumn: "Id",
                onDelete: ReferentialAction.SetNull);
        }

        /// <inheritdoc />
        protected override void Down(MigrationBuilder migrationBuilder)
        {
            migrationBuilder.DropForeignKey(
                name: "FK_calendar_events_courses_course_id",
                table: "calendar_events");

            migrationBuilder.DropTable(
                name: "calendar_event_user_states");

            migrationBuilder.DropIndex(
                name: "IX_calendar_events_course_id",
                table: "calendar_events");

            migrationBuilder.DropColumn(
                name: "calendar_event_type_colors",
                table: "users");

            migrationBuilder.DropColumn(
                name: "course_id",
                table: "calendar_events");

            migrationBuilder.DropColumn(
                name: "event_type",
                table: "calendar_events");
        }
    }
}
