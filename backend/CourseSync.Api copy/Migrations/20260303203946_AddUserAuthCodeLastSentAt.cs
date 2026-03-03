using System;
using Microsoft.EntityFrameworkCore.Migrations;

#nullable disable

namespace CourseSync.Api.Migrations
{
    /// <inheritdoc />
    public partial class AddUserAuthCodeLastSentAt : Migration
    {
        /// <inheritdoc />
        protected override void Up(MigrationBuilder migrationBuilder)
        {
            migrationBuilder.AddColumn<DateTimeOffset>(
                name: "auth_code_last_sent_at",
                table: "users",
                type: "timestamp with time zone",
                nullable: true);
        }

        /// <inheritdoc />
        protected override void Down(MigrationBuilder migrationBuilder)
        {
            migrationBuilder.DropColumn(
                name: "auth_code_last_sent_at",
                table: "users");
        }
    }
}
