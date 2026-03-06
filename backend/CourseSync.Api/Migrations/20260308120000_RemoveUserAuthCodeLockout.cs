using Microsoft.EntityFrameworkCore.Migrations;

#nullable disable

namespace CourseSync.Api.Migrations
{
    /// <inheritdoc />
    public partial class RemoveUserAuthCodeLockout : Migration
    {
        /// <inheritdoc />
        protected override void Up(MigrationBuilder migrationBuilder)
        {
            migrationBuilder.DropColumn(
                name: "auth_code_fail_count",
                table: "users");

            migrationBuilder.DropColumn(
                name: "auth_code_lockout_until",
                table: "users");
        }

        /// <inheritdoc />
        protected override void Down(MigrationBuilder migrationBuilder)
        {
            migrationBuilder.AddColumn<int>(
                name: "auth_code_fail_count",
                table: "users",
                type: "integer",
                nullable: false,
                defaultValue: 0);

            migrationBuilder.AddColumn<DateTimeOffset>(
                name: "auth_code_lockout_until",
                table: "users",
                type: "timestamp with time zone",
                nullable: true);
        }
    }
}
