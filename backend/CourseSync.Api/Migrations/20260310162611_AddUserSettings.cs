using Microsoft.EntityFrameworkCore.Migrations;

#nullable disable

namespace CourseSync.Api.Migrations
{
    /// <inheritdoc />
    public partial class AddUserSettings : Migration
    {
        /// <inheritdoc />
        protected override void Up(MigrationBuilder migrationBuilder)
        {
            migrationBuilder.AddColumn<bool>(
                name: "dark_theme_on",
                table: "users",
                type: "boolean",
                nullable: false,
                defaultValue: false);

            migrationBuilder.AddColumn<bool>(
                name: "notifications_on",
                table: "users",
                type: "boolean",
                nullable: false,
                defaultValue: false);
        }

        /// <inheritdoc />
        protected override void Down(MigrationBuilder migrationBuilder)
        {
            migrationBuilder.DropColumn(
                name: "dark_theme_on",
                table: "users");

            migrationBuilder.DropColumn(
                name: "notifications_on",
                table: "users");
        }
    }
}
